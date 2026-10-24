#include <linux/err.h>
#include <linux/fcntl.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/moduleparam.h>
#include <linux/namei.h>
#include <linux/sched.h>
#include <linux/shmem_fs.h>
#include <linux/string.h>
#include <linux/uaccess.h>
#include <linux/version.h>

#include "arch.h"
#include "klog.h" // IWYU pragma: keep
#include "compat/kernel_compat.h"
#include "preinit/preinit.h"

// NOTE: This feature is ONLY compat for Android 10 and newer devices
// Because when Android version below 10, the SELinux apply is in first_stage_init without a standalone execve call
// See more: https://cs.android.com/android/platform/superproject/+/android-9.0.0_r1:system/core/init/init.cpp;l=623
#define INIT_PATH "/system/bin/init"

// The runner is a static ELF embedded into .init.rodata by preinit/blob.S.
extern const char ksu_preinit_runner_start[];
extern const char ksu_preinit_runner_end[];

static bool ksu_preinit_enabled = true;
module_param_named(preinit, ksu_preinit_enabled, bool, 0);

// Owned here until PID 1 executes it; the init section holding the blob is
// gone by then, so it lives in an unlinked shmem file.
static struct file *ksu_preinit_file;

void __init ksu_preinit_init(void)
{
    size_t size = ksu_preinit_runner_end - ksu_preinit_runner_start;
    struct file *file;
    loff_t pos = 0;
    ssize_t written;

    if (!ksu_preinit_enabled) {
        pr_info("preinit: disabled\n");
        return;
    }
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 19, 0)
    file = shmem_file_setup("ksu_preinit", size, EMPTY_VMA_FLAGS);
#else
    file = shmem_file_setup("ksu_preinit", size, 0);
#endif
    if (IS_ERR(file)) {
        pr_err("preinit: shmem_file_setup failed: %ld\n", PTR_ERR(file));
        return;
    }

    written = ksu_kernel_write_compat(file, ksu_preinit_runner_start, size, &pos);
    if (written != size) {
        pr_err("preinit: write runner failed: %zd\n", written);
        fput(file);
        return;
    }

    ksu_preinit_file = file;
    pr_info("preinit: runner staged (%zu bytes)\n", size);
}

bool ksu_preinit_enabled_param(void)
{
    return ksu_preinit_enabled;
}

void ksu_preinit_release(void)
{
    struct file *file = xchg(&ksu_preinit_file, NULL);

    if (file) {
        pr_info("preinit: selinux_setup not seen, dropping runner\n");
        fput(file);
    }
}

void ksu_preinit_exit(void)
{
    ksu_preinit_release();
}

inline bool ksu_preinit_handle_execve_common(int *fd_ptr, int *len_ptr, char *fd_path_ptr)
{
    char fd_path[32] = { 0 };
    struct file *file;
    struct path p;
    int fd, len;

    // One shot: the runner's own exec of the real init must pass through.
    file = xchg(&ksu_preinit_file, NULL);
    if (!file)
        return false;

    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0) {
        pr_err("preinit: get fd failed: %d\n", fd);
        fput(file);
        return false;
    }
    // The fd owns the file from here on; close-on-exec frees it once the
    // runner has been loaded.
    fd_install(fd, file);

    len = snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%d", fd);
    if (kern_path(fd_path, LOOKUP_FOLLOW, &p)) {
        pr_err("preinit: %s not reachable, booting without preinit\n", fd_path);
        ksu_close_fd(fd);
        return false;
    }
    path_put(&p);

    memcpy(fd_path_ptr, fd_path, len + 1);
    *fd_ptr = fd;
    *len_ptr = len;

    return true;
}

static bool is_selinux_setup(struct user_arg_ptr *argv_user)
{
    static const char expected[] = "selinux_setup";
    char buf[sizeof(expected) + 1] = { 0 };
    const char __user *argv_user_ptr = get_user_arg_ptr(*argv_user, 1);

    if (!argv_user_ptr || IS_ERR(argv_user_ptr)) {
        pr_err("!argv_user_ptr || IS_ERR(argv_user_ptr)\n");
        return false;
    }

    if (strncpy_from_user(buf, argv_user_ptr, sizeof(buf) - 1) < 0)
        return false;

    return !strcmp(buf, expected);
}

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
#include "hook/syscall_hook.h"

bool ksu_preinit_handle_execve_tracepoint(const char __user **filename_user, const char __user *const __user *argv_user,
                                          int orig_nr, struct pt_regs *regs, long *ret)
{
    const char __user *orig_filename;
    char path[sizeof(INIT_PATH) + 1];
    char fd_path[32];
    int fd, len;

    if (likely(!READ_ONCE(ksu_preinit_file)))
        return false;

    if (!filename_user || !*filename_user)
        return false;

    memset(path, 0, sizeof(path));
    if (strncpy_from_user(
            path, (const char __user *)untagged_addr((unsigned long)ksu_task_user_ptr((unsigned long)*filename_user)),
            sizeof(path) - 1) < 0)
        return false;

    struct user_arg_ptr argv = ksu_take_user_argv(argv_user);

    if (strcmp(path, INIT_PATH) || !is_selinux_setup(&argv))
        return false;

    if (!ksu_preinit_handle_execve_common(&fd, &len, fd_path))
        return false;

    orig_filename = *filename_user;
    *filename_user = userspace_stack_buffer(fd_path, len + 1);
    if (!*filename_user) {
        *filename_user = orig_filename;
        ksu_close_fd(fd);
        return false;
    }

    pr_info("preinit: exec runner via %s\n", fd_path);
    *ret = ksu_call_syscall(orig_nr, regs);
    if (*ret < 0) {
        pr_err("preinit: exec runner failed: %ld, booting without preinit\n", *ret);
        *filename_user = orig_filename;
        ksu_close_fd(fd);
        *ret = ksu_call_syscall(orig_nr, regs);
    }
    return true;
}
#elif defined(CONFIG_KSU_MANUAL_HOOK)
void ksu_preinit_handle_execve_manual(const char **filename_ptr, struct user_arg_ptr *argv)
{
    char fd_path[32];
    int fd, len;

    char *filename = (char *)*filename_ptr;

    if (likely(!READ_ONCE(ksu_preinit_file)))
        return;

    if (!filename)
        return;

    if (strcmp(filename, INIT_PATH) || !is_selinux_setup(argv))
        return;

    if (!ksu_preinit_handle_execve_common(&fd, &len, fd_path))
        return;

    // WARNING: fd leak and fail to start when execve failed!!!
    // But we no choice, unless let user change hooker side

    pr_info("preinit: exec runner via %s\n", fd_path);

    // /system/bin/init should be bigger than /proc/self/fd/<fd>
    memset(filename, 0, strlen(filename));
    memcpy(filename, fd_path, len + 1);
}
#elif defined(CONFIG_KSU_SUSFS)
int ksu_setup_preinit_file(struct filename **filename_ptr, struct user_arg_ptr *argv_user)
{
    char fd_path[32] = { 0 };
    struct file *file;
    struct path path;
    int fd, len;

    if (likely(!READ_ONCE(ksu_preinit_file)))
        return -EINVAL;

    if (strcmp((*filename_ptr)->name, INIT_PATH) || !is_selinux_setup(argv_user))
        return -EINVAL;

    file = xchg(&ksu_preinit_file, NULL);
    if (!file)
        return -EINVAL;

    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0) {
        pr_err("preinit: get fd failed: %d\n", fd);
        fput(file);
        return -EINVAL;
    }
    // The fd owns the file from here on; close-on-exec frees it once the
    // runner has been loaded.
    fd_install(fd, file);

    len = snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%d", fd);
    if (kern_path(fd_path, LOOKUP_FOLLOW, &path)) {
        pr_err("preinit: %s not reachable, booting without preinit\n", fd_path);
        ksu_close_fd(fd);
        return -EINVAL;
    }
    path_put(&path);

    memset((void *)(*filename_ptr)->name, 0, strlen((*filename_ptr)->name));
    memcpy((void *)(*filename_ptr)->name, (void *)fd_path, strlen(fd_path));

    pr_info("preinit: exec runner via %s\n", fd_path);
    return fd;
}

int ksu_preinit_handle_post_execveat(int *fd, struct filename **filename_ptr, void *argv_user, void *envp_user,
                                     int *flags, int *retval, int *preinit_fd)
{
    if (*retval && *preinit_fd > 0) {
        ksu_close_fd(*preinit_fd);
        memset((void *)(*filename_ptr)->name, 0, strlen((*filename_ptr)->name));
        memcpy((void *)(*filename_ptr)->name, INIT_PATH, strlen(INIT_PATH));
        return -EINVAL;
    }
    return 0;
}
#else
#error Unsupported hook type
#endif
