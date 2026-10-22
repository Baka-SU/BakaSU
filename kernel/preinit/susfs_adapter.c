/* SPDX-License-Identifier: GPL-2.0-only */
/* SUSFS preinit interface for BakaSU built-in kernels.
 * Only PID 1 ever enters this path. See patch_susfs_exec.py for early-error
 * handling in the *separate* target Linux kernel's fs/exec.c.
 */
#include <linux/compat.h>
#include <linux/err.h>
#include <linux/fcntl.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/namei.h>
#include <linux/sched.h>
#include <linux/string.h>
#include <linux/uaccess.h>

#include "compat/kernel_compat.h"
#include "runtime/ksud.h"
#include "preinit/preinit.h"

#define KSU_PREINIT_INIT_PATH "/system/bin/init"

#ifndef KSU_NO_PREINIT
/* Only PID 1 may own this, so no other task races the saved name. */
static struct filename *ksu_preinit_saved_filename;

static bool is_preinit_selinux_setup(struct user_arg_ptr *argv)
{
    const char __user *arg = NULL;
    char stage[sizeof("selinux_setup") + 1] = {0};

    if (!argv)
        return false;
#ifdef CONFIG_COMPAT
    if (argv->is_compat) {
        compat_uptr_t addr;
        if (!argv->ptr.compat || get_user(addr, argv->ptr.compat + 1))
            return false;
        arg = compat_ptr(addr);
    } else
#endif
    {
        if (!argv->ptr.native || get_user(arg, argv->ptr.native + 1))
            return false;
    }
    if (!arg || strncpy_from_user(stage, arg, sizeof(stage) - 1) < 0)
        return false;
    return !strcmp(stage, "selinux_setup");
}

/* Called by the SUSFS-patched do_execveat_common() before alloc_bprm(). */
int ksu_setup_preinit_file(struct filename **filename_ptr, struct user_arg_ptr *argv)
{
    struct file *runner;
    struct filename *replacement;
    struct path path;
    char fd_path[32];
    int fd, len;

    if (current->pid != 1 || !filename_ptr || !*filename_ptr ||
        IS_ERR(*filename_ptr) || ksu_preinit_saved_filename)
        return -EINVAL;
    if (strcmp((*filename_ptr)->name, KSU_PREINIT_INIT_PATH) ||
        !is_preinit_selinux_setup(argv))
        return -EINVAL;

    runner = ksu_preinit_take_file();
    if (!runner)
        return -ENOENT;
    /* Existing SUSFS hook uses positive fd as its interception sentinel. */
    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd <= 0) {
        if (fd == 0)
            put_unused_fd(fd);
        fput(runner);
        return -EINVAL;
    }
    fd_install(fd, runner);

    len = snprintf(fd_path, sizeof(fd_path), "/proc/self/fd/%d", fd);
    if (len <= 0 || len >= sizeof(fd_path))
        goto out_close;
    if (kern_path(fd_path, LOOKUP_FOLLOW, &path))
        goto out_close;
    path_put(&path);

    replacement = getname_kernel(fd_path);
    if (IS_ERR(replacement))
        goto out_close;
    ksu_preinit_saved_filename = *filename_ptr;
    *filename_ptr = replacement;
    pr_info("preinit: PID1 exec runner via %s\n", fd_path);
    return fd;

out_close:
    ksu_close_fd(fd);
    return -EINVAL;
}

/* On failure restore original init filename before upstream retries. */
int ksu_preinit_handle_post_execveat(int *fd, struct filename **filename_ptr,
                                    void *argv, void *envp, int *flags,
                                    int *retval, int *preinit_fd)
{
    struct filename *replacement;
    (void)fd;
    (void)argv;
    (void)envp;
    (void)flags;
    if (!ksu_preinit_saved_filename)
        return 0;
    if (!filename_ptr || !*filename_ptr || !retval || !preinit_fd)
        return 0;

    if (*retval < 0) {
        replacement = *filename_ptr;
        *filename_ptr = ksu_preinit_saved_filename;
        ksu_preinit_saved_filename = NULL;
        putname(replacement);
        if (*preinit_fd > 0)
            ksu_close_fd(*preinit_fd);
        *preinit_fd = 0;
        pr_warn("preinit: runner exec failed (%d), retry normal init\n", *retval);
        return 1;
    }
    putname(ksu_preinit_saved_filename);
    ksu_preinit_saved_filename = NULL;
    *preinit_fd = 0;
    return 0;
}
#else
int ksu_setup_preinit_file(struct filename **filename_ptr, struct user_arg_ptr *argv)
{
    return -EOPNOTSUPP;
}
int ksu_preinit_handle_post_execveat(int *fd, struct filename **filename_ptr,
                                    void *argv, void *envp, int *flags,
                                    int *retval, int *preinit_fd)
{
    return 0;
}
#endif
