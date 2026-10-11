#include "selinux_hide.h"
#include <linux/cred.h>
#include <linux/cpu.h>
#include <linux/memory.h>
#include <linux/uaccess.h>
#include <linux/init.h>
#include <linux/printk.h>
#include <linux/ratelimit.h>
#include <linux/string.h>
#include <linux/fs.h>
#include <asm-generic/errno-base.h>
#include <net/genetlink.h>
#include <linux/moduleparam.h>
#include <linux/mutex.h>
#include <linux/version.h>
#include <linux/jump_label.h>
#include <linux/rcupdate.h>
#include <linux/rwlock_types.h>
#include <linux/jump_label.h>
#include <selinux/sepolicy.h>
#include <ss/policydb.h>

// security/selinux/include/security.h
#include <security.h>
#include <ss/context.h>
#include <ss/services.h>
#include <ss/mls.h>
#include <ss/conditional.h>

#include "avc.h"
#include "klog.h" // IWYU pragma: keep
#include "linux/kallsyms.h"
#include "objsec.h"
#include "hook/patch_memory.h"
#include "ksu.h"
#include "policy/feature.h"
#include "infra/symbol_resolver.h"
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
#include "hook/lsm_hook_magic.h"
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(4, 2, 0) || defined(KSU_COMPAT_HAS_LIST_OF_LSM_HOOKS)
#include <linux/lsm_hooks.h>
#endif

#include "selinux/selinux.h"
#include "selinux/sepolicy.h"
#include "selinux_hide.h"
#include "compat/kernel_compat.h"

#ifdef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE
#define __maybe_static
#else
#define __maybe_static static
#endif

static DEFINE_MUTEX(selinux_hide_mutex);
__maybe_static bool ksu_selinux_hide_enabled __read_mostly = false;
// remove static in susfs
__maybe_static bool ksu_selinux_hide_running __read_mostly = false;

#ifdef KSU_COMPAT_USE_STATIC_KEY
// We should talk to you, susfs
// Why use manual hook instead of auto hook
__maybe_static DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);
#else
static bool fake_status_initialize_key __read_mostly = false;
#endif

__maybe_static struct page *fake_status = NULL;
static struct mutex *ksu_selinux_status_lock = NULL;
__maybe_static void initialize_fake_status();

#ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE
enum sel_inos {
    SEL_ROOT_INO = 2,
    SEL_LOAD, /* load policy */
    SEL_ENFORCE, /* get or set enforcing status */
    SEL_CONTEXT, /* validate context */
    SEL_ACCESS, /* compute access decision */
    SEL_CREATE, /* compute create labeling decision */
    SEL_RELABEL, /* compute relabeling decision */
    SEL_USER, /* compute reachable user contexts */
    SEL_POLICYVERS, /* return policy version for this kernel */
    SEL_COMMIT_BOOLS, /* commit new boolean values */
    SEL_MLS, /* return if MLS policy is enabled */
    SEL_DISABLE, /* disable SELinux until next reboot */
    SEL_MEMBER, /* compute polyinstantiation membership decision */
    SEL_CHECKREQPROT, /* check requested protection, not kernel-applied one */
    SEL_COMPAT_NET, /* whether to use old compat network packet controls */
    SEL_REJECT_UNKNOWN, /* export unknown reject handling to userspace */
    SEL_DENY_UNKNOWN, /* export unknown deny handling to userspace */
    SEL_STATUS, /* export current status using mmap() */
    SEL_POLICY, /* allow userspace to read the in kernel policy */
    SEL_VALIDATE_TRANS, /* compute validatetrans decision */
    SEL_INO_NEXT, /* The next inode number to use */
};

typedef ssize_t (*write_op_fn)(struct file *, char *, size_t);

static write_op_fn *selinux_write_op;

#endif // #ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE

static int ksu_security_context_to_sid(struct policydb *orig_policydb, struct sidtab *orig_sidtab,
                                       struct policydb *policydb, struct sidtab *sidtab, const char *scontext,
                                       u32 scontext_len, u32 *sid, u32 def_sid, gfp_t gfp_flags, u32 *orig_sid_p,
                                       int *orig_rc_p);
static int ksu_security_sid_to_context(struct policydb *policydb, struct sidtab *sidtab, u32 sid, char **scontext,
                                       u32 *scontext_len);
static __nocfi void ksu_security_compute_av_user(struct policydb *policydb, struct sidtab *sidtab, u32 ssid, u32 tsid,
                                                 u16 tclass, struct av_decision *avd);

// clang-format off
// WARN: ifdef hell
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0) || defined(KSU_COMPAT_USE_SELINUX_STATE)
    static void (*security_dump_masked_av_fn)(struct policydb *policydb, struct context *scontext, struct context *tcontext,
                                            u16 tclass, u32 permissions, const char *reason) = NULL;
    static void (*context_struct_compute_av_fn)(struct policydb *policydb, struct context *scontext,
                                                struct context *tcontext, u16 tclass, struct av_decision *avd,
                                                struct extended_perms *xperms) = NULL;
#else
    // mostly 4.14-
    struct sidtab* sidtab_ptr;

    // compat wrapper
    static void (*legacy_security_dump_masked_av_fn)(struct context *scontext, struct context *tcontext,
                                            u16 tclass, u32 permissions, const char *reason) = NULL;
    #if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 3, 0) || defined(KSU_COMPAT_HAS_EXTENDED_PERMS)
        // extended_perms add in 4.3
        static void (*legacy_context_struct_compute_av_fn)(struct context *scontext,
                                                    struct context *tcontext, u16 tclass, struct av_decision *avd,
                                                    struct extended_perms *xperms) = NULL;

        static void ksu_context_struct_compute_av_fn(struct policydb *policydb, struct context *scontext,
                                                    struct context *tcontext, u16 tclass, struct av_decision *avd,
                                                    struct extended_perms *xperms)
        {
            if (legacy_context_struct_compute_av_fn) {
                legacy_context_struct_compute_av_fn(scontext, tcontext, tclass, avd, xperms);
            }
        }
    #else
        struct extended_perms {
            /* I am a placeholder, I just want to make the compiler happy */   
        }
        static void (*legacy_context_struct_compute_av_fn)(struct context *scontext,
                                                    struct context *tcontext, u16 tclass, struct av_decision *avd) = NULL;

        static void ksu_context_struct_compute_av_fn(struct policydb *policydb, struct context *scontext,
                                                    struct context *tcontext, u16 tclass, struct av_decision *avd,
                                                    struct extended_perms *xperms)
        {
            if (legacy_security_dump_masked_av_fn) {
                legacy_security_dump_masked_av_fn(scontext, tcontext, tclass, avd);
            }
        }
    #endif
    static void ksu_security_dump_masked_av(struct policydb *policydb, struct context *scontext, struct context *tcontext,
                                            u16 tclass, u32 permissions, const char *reason)
    {
        if (legacy_security_dump_masked_av_fn) {
            legacy_security_dump_masked_av_fn(scontext, tcontext, tclass, permissions, reason);
        }
    }

    static void (*security_dump_masked_av_fn)(struct policydb *policydb, struct context *scontext, struct context *tcontext,
                                            u16 tclass, u32 permissions, const char *reason) = ksu_security_dump_masked_av;
    static void (*context_struct_compute_av_fn)(struct policydb *policydb, struct context *scontext,
                                                struct context *tcontext, u16 tclass, struct av_decision *avd,
                                                struct extended_perms *xperms) = ksu_context_struct_compute_av_fn;
#endif
// clang-format on

// remove static in susfs
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
__maybe_static int security_context_to_sid_with_policy(struct selinux_policy *policy, const char *scontext,
                                                       u32 scontext_len, u32 *sid, u32 def_sid, gfp_t gfp_flags,
                                                       u32 *orig_sid_p, int *orig_rc_p);

__maybe_static int security_sid_to_context_with_policy(struct selinux_policy *policy, u32 sid, char **scontext,
                                                       u32 *scontext_len)
{
    struct policydb *policydb;
    struct sidtab *sidtab;

    // removed: if (!selinux_initialized())
    // removed: rcu lock
    policydb = &policy->policydb;
    sidtab = policy->sidtab;

    return ksu_security_sid_to_context(policydb, sidtab, sid, scontext, scontext_len);
}

__maybe_static void security_compute_av_user_with_policy(struct selinux_policy *policy, u32 ssid, u32 tsid, u16 tclass,
                                                         struct av_decision *avd)
{
    struct policydb *policydb;
    struct sidtab *sidtab;

    // remove: rcu lock
    // remove: if (!selinux_initialized())

    policydb = &policy->policydb;
    sidtab = policy->sidtab;

    ksu_security_compute_av_user(policydb, sidtab, ssid, tsid, tclass, avd);
}
#endif

#ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE

static write_op_fn *context_write, *access_write;
static write_op_fn orig_context_write, orig_access_write;

// 6.6+ or 4.14-
// android has backport in 4.14
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 6, 0) ||                                                                   \
    (LINUX_VERSION_CODE < KERNEL_VERSION(5, 10, 0) && !defined(KSU_COMPAT_USE_SELINUX_STATE))
#define ksu_avc_has_perm_compat(...) avc_has_perm(__VA_ARGS__)
#define ksu_security_bounded_transition_compat(...) security_bounded_transition(__VA_ARGS__)
#else
#define ksu_avc_has_perm_compat(...) avc_has_perm(&selinux_state, __VA_ARGS__)
#define ksu_security_bounded_transition_compat(...) security_bounded_transition(&selinux_state, __VA_ARGS__)
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 18, 0)
#define ksu_task_security_struct task_security_struct
#else
#define ksu_task_security_struct cred_security_struct
#endif

#ifndef KSU_COMPAT_HAS_CURRENT_SID
static inline u32 current_sid(void)
{
    const struct ksu_task_security_struct *tsec = selinux_cred(current_cred());

    return tsec->sid;
}
#endif

static ssize_t my_write_context(struct file *file, char *buf, size_t size)
{
    // apply to all app uids
    if (likely(ksu_get_uid_t(current_uid()) < 10000)) {
        return orig_context_write(file, buf, size);
    }
    char *canon = NULL;
    u32 sid, len;
    ssize_t length;

    length =
        ksu_avc_has_perm_compat(current_sid(), SECINITSID_SECURITY, SECCLASS_SECURITY, SECURITY__CHECK_CONTEXT, NULL);
    if (length)
        goto out;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    length = security_context_to_sid_with_policy(backup_sepolicy, buf, size, &sid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
    if (length) {
        goto out;
    }

    length = security_sid_to_context_with_policy(backup_sepolicy, sid, &canon, &len);
    if (length)
        goto out;
#elif defined(KSU_COMPAT_USE_SELINUX_STATE)
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0) || defined(KSU_COMPAT_SIDTAB_AS_REFERENCE)
    length = ksu_security_context_to_sid(&selinux_state.ss->policydb, selinux_state.ss->sidtab, backup_policydb,
                                         backup_sidtab, buf, size, &sid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
#else
    length = ksu_security_context_to_sid(&selinux_state.ss->policydb, &selinux_state.ss->sidtab, backup_policydb,
                                         backup_sidtab, buf, size, &sid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
#endif
    if (length)
        goto out;

    length = ksu_security_sid_to_context(backup_policydb, backup_sidtab, sid, &canon, &len);
    if (length)
        goto out;
#else
    length = ksu_security_context_to_sid(&policydb, sidtab_ptr, backup_policydb, backup_sidtab, buf, size, &sid,
                                         SECSID_NULL, GFP_KERNEL, NULL, NULL);
    if (length)
        goto out;

    length = ksu_security_sid_to_context(backup_policydb, backup_sidtab, sid, &canon, &len);
    if (length)
        goto out;
#endif

    length = -ERANGE;
    if (len > SIMPLE_TRANSACTION_LIMIT) {
        pr_err("SELinux: %s:  context size (%u) exceeds "
               "payload max\n",
               __func__, len);
        goto out;
    }

    memcpy(buf, canon, len);
    length = len;
out:
    kfree(canon);
    return length;
}

static ssize_t my_write_access(struct file *file, char *buf, size_t size)
{
    // apply to all app uids
    if (likely(ksu_get_uid_t(current_uid()) < 10000)) {
        return orig_access_write(file, buf, size);
    }
    char *scon = NULL, *tcon = NULL;
    u32 ssid, tsid, sconlen, tconlen;
    u16 tclass;
    struct av_decision avd;
    ssize_t length;

    length = ksu_avc_has_perm_compat(current_sid(), SECINITSID_SECURITY, SECCLASS_SECURITY, SECURITY__COMPUTE_AV, NULL);
    if (length)
        goto out;

    length = -ENOMEM;
    scon = kzalloc(size + 1, GFP_KERNEL);
    if (!scon)
        goto out;

    length = -ENOMEM;
    tcon = kzalloc(size + 1, GFP_KERNEL);
    if (!tcon)
        goto out;

    length = -EINVAL;
    if (sscanf(buf, "%s %s %hu", scon, tcon, &tclass) != 3)
        goto out;

    sconlen = strlen(scon);
    tconlen = strlen(tcon);

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    length =
        security_context_to_sid_with_policy(backup_sepolicy, scon, sconlen, &ssid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
    if (length) {
        goto out;
    }

    length =
        security_context_to_sid_with_policy(backup_sepolicy, tcon, tconlen, &tsid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
    if (length) {
        goto out;
    }

    security_compute_av_user_with_policy(backup_sepolicy, ssid, tsid, tclass, &avd);
#elif defined(KSU_COMPAT_USE_SELINUX_STATE)
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0) || defined(KSU_COMPAT_SIDTAB_AS_REFERENCE)
    length = ksu_security_context_to_sid(&selinux_state.ss->policydb, selinux_state.ss->sidtab, backup_policydb,
                                         backup_sidtab, scon, sconlen, &ssid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
#else
    length = ksu_security_context_to_sid(&selinux_state.ss->policydb, &selinux_state.ss->sidtab, backup_policydb,
                                         backup_sidtab, scon, sconlen, &ssid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
#endif
    if (length)
        goto out;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0) || defined(KSU_COMPAT_SIDTAB_AS_REFERENCE)
    length = ksu_security_context_to_sid(&selinux_state.ss->policydb, selinux_state.ss->sidtab, backup_policydb,
                                         backup_sidtab, tcon, tconlen, &tsid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
#else
    length = ksu_security_context_to_sid(&selinux_state.ss->policydb, &selinux_state.ss->sidtab, backup_policydb,
                                         backup_sidtab, tcon, tconlen, &tsid, SECSID_NULL, GFP_KERNEL, NULL, NULL);
#endif
    if (length)
        goto out;

    ksu_security_compute_av_user(backup_policydb, backup_sidtab, ssid, tsid, tclass, &avd);
#else
    length = ksu_security_context_to_sid(&policydb, sidtab_ptr, backup_policydb, backup_sidtab, scon, sconlen, &ssid,
                                         SECSID_NULL, GFP_KERNEL, NULL, NULL);
    if (length)
        goto out;

    length = ksu_security_context_to_sid(&policydb, sidtab_ptr, backup_policydb, backup_sidtab, tcon, tconlen, &tsid,
                                         SECSID_NULL, GFP_KERNEL, NULL, NULL);
    if (length)
        goto out;

    ksu_security_compute_av_user(backup_policydb, backup_sidtab, ssid, tsid, tclass, &avd);
#endif

    // stock reads 1; a loader load_policy may have bumped the backup before we load
    avd.seqno = 1;
    length = scnprintf(buf, SIMPLE_TRANSACTION_LIMIT, "%x %x %x %x %u %x", avd.allowed, 0xffffffff, avd.auditallow,
                       avd.auditdeny, avd.seqno, avd.flags);
out:
    kfree(tcon);
    kfree(scon);
    return length;
}

/*
 * get the security ID of a set of credentials
 */
static inline u32 cred_sid(const struct cred *cred)
{
    const struct ksu_task_security_struct *tsec;

    tsec = selinux_cred(cred);
    return tsec->sid;
}

/*
 * get the objective security ID of a task
 */
static inline u32 task_sid_obj(const struct task_struct *task)
{
    u32 sid;

    rcu_read_lock();
    sid = cred_sid(__task_cred(task));
    rcu_read_unlock();
    return sid;
}

static u32 ptrace_parent_sid(void)
{
    u32 sid = 0;
    struct task_struct *tracer;

    rcu_read_lock();
    tracer = ptrace_parent(current);
    if (tracer)
        sid = task_sid_obj(tracer);
    rcu_read_unlock();

    return sid;
}

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) && !defined(KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE)
struct ksu_lsm_hook selinux_setprocattr_hook =
    KSU_LSM_HOOK_INIT(setprocattr, "selinux_setprocattr", ksu_handle_selinux_setprocattr, 0);
#endif

#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 10, 0) &&                                                                   \
    (LINUX_VERSION_CODE >= KERNEL_VERSION(4, 2, 0) || defined(KSU_COMPAT_HAS_LIST_OF_LSM_HOOKS))
static setprocattr_fn ksu_orig_setprocattr;
uintptr_t selinux_setprocattr_hook_ptr = 0;
#else
extern setprocattr_fn ksu_orig_setprocattr;
#endif

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0) || defined(KSU_COMPAT_SETPROCATTR_USE_NEW_PROTOTYPE)
int __nocfi ksu_handle_selinux_setprocattr(const char *name, void *value, size_t size)
#else
int __nocfi ksu_handle_selinux_setprocattr(struct task_struct *p, char *name, void *value, size_t size)
#endif
{
    if (likely(ksu_get_uid_t(current_uid()) < 10000)) {
        goto call_orig;
    }

    if (strcmp(name, "current")) {
        goto call_orig;
    }

    struct ksu_task_security_struct *tsec;
    struct cred *new;
    u32 mysid = current_sid(), sid = 0, tmp_sid, ptsid;
    int error, error2;
    char *str = value;

    error = ksu_avc_has_perm_compat(mysid, mysid, SECCLASS_PROCESS, PROCESS__SETCURRENT, NULL);
    if (error)
        return error;

    /* Obtain a SID for the context, if one was specified. */
    if (size && str[0] && str[0] != '\n') {
        if (str[size - 1] == '\n') {
            str[size - 1] = 0;
            size--;
        }
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
        error2 = security_context_to_sid_with_policy(backup_sepolicy, value, size, &tmp_sid, SECSID_NULL, GFP_KERNEL,
                                                     &sid, &error);
#elif (LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0) || defined(KSU_COMPAT_SIDTAB_AS_REFERENCE)) &&                    \
    defined(KSU_COMPAT_USE_SELINUX_STATE)
        error2 =
            ksu_security_context_to_sid(&selinux_state.ss->policydb, selinux_state.ss->sidtab, backup_policydb,
                                        backup_sidtab, value, size, &tmp_sid, SECSID_NULL, GFP_KERNEL, &sid, &error);
#elif defined(KSU_COMPAT_USE_SELINUX_STATE)
        error2 =
            ksu_security_context_to_sid(&selinux_state.ss->policydb, &selinux_state.ss->sidtab, backup_policydb,
                                        backup_sidtab, value, size, &tmp_sid, SECSID_NULL, GFP_KERNEL, &sid, &error);
#else
        error2 = ksu_security_context_to_sid(&policydb, sidtab_ptr, backup_policydb, backup_sidtab, value, size,
                                             &tmp_sid, SECSID_NULL, GFP_KERNEL, &sid, &error);
#endif
        if (error2 || error)
            return error2 ?: error;
    }

    new = prepare_creds();
    if (!new)
        return -ENOMEM;

    /* Permission checking based on the specified context is
	   performed during the actual operation (execve,
	   open/mkdir/...), when we know the full context of the
	   operation.  See selinux_bprm_creds_for_exec for the execve
	   checks and may_create for the file creation checks. The
	   operation will then fail if the context is not permitted. */
    tsec = selinux_cred(new);
    error = -EINVAL;
    if (sid == 0)
        goto abort_change;

    if (!current_is_single_threaded()) {
        error = ksu_security_bounded_transition_compat(tsec->sid, sid);
        if (error)
            goto abort_change;
    }

    /* Check permissions for the transition. */
    error = ksu_avc_has_perm_compat(tsec->sid, sid, SECCLASS_PROCESS, PROCESS__DYNTRANSITION, NULL);
    if (error)
        goto abort_change;

    /* Check for ptracing, and update the task SID if ok.
        Otherwise, leave SID unchanged and fail. */
    ptsid = ptrace_parent_sid();
    if (ptsid != 0) {
        error = ksu_avc_has_perm_compat(ptsid, sid, SECCLASS_PROCESS, PROCESS__PTRACE, NULL);
        if (error)
            goto abort_change;
    }

    tsec->sid = sid;

    commit_creds(new);
    return size;

abort_change:
    abort_creds(new);
    return error;

call_orig:
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    return ((setprocattr_fn)selinux_setprocattr_hook.original)(name, value, size);
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0) || defined(KSU_COMPAT_SETPROCATTR_USE_NEW_PROTOTYPE)
    return ksu_orig_setprocattr(name, value, size);
#else
    return ksu_orig_setprocattr(p, name, value, size);
#endif
}

typedef int (*sel_open_handle_status_fn)(struct inode *inode, struct file *filp);
static sel_open_handle_status_fn orig_sel_open_handle_status, *sel_open_handle_status_slot;
static int my_sel_open_handle_status(struct inode *inode, struct file *filp)
{
    if (likely(ksu_get_uid_t(current_uid()) >= 10000 && ksu_selinux_hide_enabled)) {
        void *data;
        mutex_lock(ksu_selinux_status_lock);
        data = fake_status;
        mutex_unlock(ksu_selinux_status_lock);
        if (data) {
            filp->private_data = data;
            return 0;
        }
    }

    int ret = orig_sel_open_handle_status(inode, filp);
#ifdef KSU_COMPAT_USE_STATIC_KEY
    if (static_branch_unlikely(&fake_status_initialize_key) && !ret && !fake_status) {
        initialize_fake_status();
    }
#else
    if (!fake_status_initialize_key && !ret && !fake_status) {
        initialize_fake_status();
    }
#endif
    return ret;
}

static void hook_selinux_status_open()
{
    if (orig_sel_open_handle_status)
        return;
    if (!sel_open_handle_status_slot) {
#ifdef CONFIG_KALLSYMS_ALL
        struct file_operations *ops = (struct file_operations *)find_kernel_symbol_exact("sel_handle_status_ops");
#else
        extern struct file_operations sel_handle_status_ops;
        struct file_operations *ops = &sel_handle_status_ops;
#endif
        if (!ops) {
            pr_err("selinux_hide: sel_handle_status_ops not found, fake status will not work\n");
            return;
        }
        sel_open_handle_status_slot = &ops->open;
    }
    sel_open_handle_status_fn new_fn = my_sel_open_handle_status;
    orig_sel_open_handle_status = *sel_open_handle_status_slot;
    int ret = ksu_patch_text(sel_open_handle_status_slot, &new_fn, sizeof(new_fn), KSU_PATCH_TEXT_FLUSH_DCACHE);
    if (ret) {
        pr_err("selinux_hide: init: patch_text sel_open_handle_status err: %d\n", ret);
        sel_open_handle_status_slot = NULL;
        orig_sel_open_handle_status = NULL;
    }
}

extern void ksu_unregister_setprocattr_lsm_hook();

static void ksu_selinux_hide_unhook()
{
    int ret;
    if (orig_context_write) {
        ret =
            ksu_patch_text(context_write, &orig_context_write, sizeof(orig_context_write), KSU_PATCH_TEXT_FLUSH_DCACHE);
        orig_context_write = NULL;
        if (ret) {
            pr_err("selinux_hide: exit: patch_text context_write err: %d\n", ret);
        }
    }
    if (orig_access_write) {
        ret = ksu_patch_text(access_write, &orig_access_write, sizeof(orig_access_write), KSU_PATCH_TEXT_FLUSH_DCACHE);
        orig_access_write = NULL;
        if (ret) {
            pr_err("selinux_hide: exit: patch_text access_write err: %d\n", ret);
        }
    }
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    ksu_lsm_unhook(&selinux_setprocattr_hook);
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(4, 2, 0) || defined(KSU_COMPAT_HAS_LIST_OF_LSM_HOOKS)
    if (ksu_orig_setprocattr) {
        ret = ksu_patch_text((void *)selinux_setprocattr_hook_ptr, &ksu_orig_setprocattr, sizeof(ksu_orig_setprocattr),
                             KSU_PATCH_TEXT_FLUSH_DCACHE);
        ksu_orig_setprocattr = NULL;
        if (ret) {
            pr_err("selinux_hide: exit: patch_text setprocattr err: %d\n", ret);
        }
    }
#else
    stop_machine(ksu_unregister_setprocattr_lsm_hook, NULL, NULL);
#endif
}

extern void ksu_register_setprocattr_lsm_hook();
#else
#define ksu_selinux_hide_unhook()                                                                                      \
    do {                                                                                                               \
    } while (0)
#endif // #ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE

static int ksu_selinux_hide_enable()
{
    int ret;
    pr_info("selinux_hide: init selinux hide\n");
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    if (!backup_sepolicy) {
        pr_err("no backup sepolicy available, please save feature and reboot to retry!\n");
        return -EAGAIN;
    }
#else
    if (!backup_policydb) {
        pr_err("no backup policydb available, please save feature and reboot to retry!\n");
        return -EAGAIN;
    }

    if (!backup_sidtab) {
        pr_err("no backup sidtab available, please save feature and reboot to retry!\n");
        return -EAGAIN;
    }
#endif

#ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE
    hook_selinux_status_open();
#endif

    // clang-format off
#ifdef CONFIG_KALLSYMS_ALL
    #if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0) || defined(KSU_COMPAT_USE_SELINUX_STATE)
        security_dump_masked_av_fn = ksu_resolve_symbol_for_functable_hook("security_dump_masked_av");
        if (!security_dump_masked_av_fn) {
            pr_warn("security_dump_masked_av not found!\n");
        }
        context_struct_compute_av_fn = ksu_resolve_symbol_for_functable_hook("context_struct_compute_av");
        if (!context_struct_compute_av_fn) {
            pr_warn("context_struct_compute_av not found!\n");
        }
    #else
        legacy_security_dump_masked_av_fn = ksu_resolve_symbol_for_functable_hook("security_dump_masked_av");
        if (!legacy_security_dump_masked_av_fn) {
            pr_warn("security_dump_masked_av not found!\n");
        }
        legacy_context_struct_compute_av_fn = ksu_resolve_symbol_for_functable_hook("context_struct_compute_av");
        if (!legacy_context_struct_compute_av_fn) {
            pr_warn("context_struct_compute_av not found!\n");
        }
    #endif

    #if LINUX_VERSION_CODE < KERNEL_VERSION(5, 10, 0) && !defined(KSU_COMPAT_USE_SELINUX_STATE)
        sidtab_ptr = ksu_resolve_symbol_for_functable_hook("sidtab");
        if (!sidtab_ptr) {
            pr_err("sidtab can not find!");
            return -EFAULT;
        }
    #endif
#else
    #if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0) || defined(KSU_COMPAT_USE_SELINUX_STATE)
        extern void security_dump_masked_av(struct policydb * policydb, struct context * scontext,
                                            struct context * tcontext, u16 tclass, u32 permissions, const char *reason);
        extern void context_struct_compute_av(struct policydb * policydb, struct context * scontext,
                                            struct context * tcontext, u16 tclass, struct av_decision * avd,
                                            struct extended_perms * xperms);

        security_dump_masked_av_fn = &security_dump_masked_av;
        if (!security_dump_masked_av_fn) {
            pr_warn("security_dump_masked_av not found!\n");
        }

        context_struct_compute_av_fn = &context_struct_compute_av;
        if (!context_struct_compute_av_fn) {
            pr_warn("context_struct_compute_av not found!\n");
        }
    #else
        extern void security_dump_masked_av(struct context *scontext, struct context *tcontext,
                                                        u16 tclass, u32 permissions, const char *reason);
        #if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 3, 0) || defined(KSU_COMPAT_HAS_EXTENDED_PERMS)
        extern void context_struct_compute_av(struct context *scontext,
                                                struct context *tcontext, u16 tclass, struct av_decision *avd,
                                                struct extended_perms *xperms);
        #else
        extern void context_struct_compute_av(struct context *scontext, 
                                                struct context *tcontext, u16 tclass, struct av_decision *avd);
        #endif

        legacy_security_dump_masked_av_fn = &security_dump_masked_av;
        if (!legacy_security_dump_masked_av_fn) {
            pr_warn("security_dump_masked_av not found!\n");
        }

        legacy_context_struct_compute_av_fn = &context_struct_compute_av;
        if (!legacy_context_struct_compute_av_fn) {
            pr_warn("context_struct_compute_av not found!\n");
        }
    #endif

    #if LINUX_VERSION_CODE < KERNEL_VERSION(5, 10, 0) && !defined(KSU_COMPAT_USE_SELINUX_STATE)
        extern struct sidtab sidtab;

        sidtab_ptr = &sidtab;
    #endif
#endif
    // clang-format on

#ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE
#ifdef CONFIG_KALLSYMS_ALL
    selinux_write_op = (write_op_fn *)find_kernel_symbol_exact("write_op");
#else
    extern ssize_t (*const write_op[])(struct file *, char *, size_t);

    selinux_write_op = (write_op_fn *)&write_op;
#endif
    if (!selinux_write_op) {
        pr_err("selinux_hide: no write_op found!\n");
        return -ENOSYS;
    }

    context_write = &selinux_write_op[SEL_CONTEXT];
    pr_info("selinux_hide: context_write: 0x%lx [%pSb]\n", (unsigned long)*context_write, *context_write);
    write_op_fn my = my_write_context;
    orig_context_write = *context_write;
    ret = ksu_patch_text(context_write, &my, sizeof(my), KSU_PATCH_TEXT_FLUSH_DCACHE);
    if (ret) {
        pr_err("selinux_hide: init: patch_text context_write err: %d\n", ret);
        goto unhook;
    }

    access_write = &selinux_write_op[SEL_ACCESS];
    pr_info("selinux_hide: access_write: 0x%lx [%pSb]\n", (unsigned long)*access_write, *access_write);
    my = my_write_access;
    orig_access_write = *access_write;
    ret = ksu_patch_text(access_write, &my, sizeof(my), KSU_PATCH_TEXT_FLUSH_DCACHE);
    if (ret) {
        pr_err("selinux_hide: init: patch_text access_write err: %d\n", ret);
        goto unhook;
    }

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)
    ret = ksu_lsm_hook(&selinux_setprocattr_hook);
    if (ret) {
        pr_err("selinux_hide: init: selinux_setprocattr_hook err: %d\n", ret);
        goto unhook;
    }
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(4, 2, 0) || defined(KSU_COMPAT_HAS_LIST_OF_LSM_HOOKS)
    struct security_hook_list *hp;

    // https://github.com/torvalds/linux/commit/df0ce17331e2501dbffc060041dfc6c5f85227b5
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0) || defined(KSU_COMPAT_HLIST_FOR_SECURITY_HOOK_LIST)
#define ksu_for_each_lsm_entry hlist_for_each_entry
#else
#define ksu_for_each_lsm_entry list_for_each_entry
#endif

    ksu_for_each_lsm_entry(hp, &security_hook_heads.setprocattr, list)
    {
        // https://github.com/torvalds/linux/commit/d69dece5f5b6bc7a5e39d2b6136ddc69469331fe
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0) || defined(KSU_COMPAT_REQUIRE_PROVIDE_LSM_NAME)
        // when we are in 4.11+, we can ensure we are control "selinux" LSM by that
        if (strcmp("selinux", hp->lsm))
            continue;
#endif // #if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)
        selinux_setprocattr_hook_ptr = (unsigned long)&hp->hook.setprocattr;
        ksu_orig_setprocattr = hp->hook.setprocattr;
        setprocattr_fn my_setprocattr = ksu_handle_selinux_setprocattr;
        ret =
            ksu_patch_text(&hp->hook.setprocattr, &my_setprocattr, sizeof(my_setprocattr), KSU_PATCH_TEXT_FLUSH_DCACHE);
        if (ret) {
            pr_err("selinux_hide: init: patch_text selinux setprocattr err: %d\n", ret);
            goto unhook;
        }
        goto out;
    }

#undef ksu_for_each_lsm_entry
out:
#else
    // for 4.2-, We handle it in lsm_hooks.c

    stop_machine(ksu_register_setprocattr_lsm_hook, NULL, NULL);
#endif // #if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0)

#endif // #ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE

    return 0;

#ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE
unhook:
#endif
    ksu_selinux_hide_unhook();
    return -ENOSYS;
}

static void ksu_selinux_hide_disable()
{
    pr_info("selinux_hide: exit selinux hide\n");

    ksu_selinux_hide_unhook();
}

static int selinux_hide_feature_get(u64 *value)
{
    *value = ksu_selinux_hide_enabled ? 1 : 0;
    return 0;
}

static int selinux_hide_feature_set(u64 value)
{
    bool enable = value != 0;
    int ret = 0;
    pr_info("selinux_hide: set to %d\n", enable);
    mutex_lock(&selinux_hide_mutex);
    ksu_selinux_hide_enabled = enable;
    if (enable) {
        if (!ksu_selinux_hide_running) {
            ret = ksu_selinux_hide_enable();
            if (!ret) {
                ksu_selinux_hide_running = true;
            }
        }
    } else {
        if (ksu_selinux_hide_running) {
            ksu_selinux_hide_disable();
            ksu_selinux_hide_running = false;
        }
    }
    mutex_unlock(&selinux_hide_mutex);
    return ret;
}

static const struct ksu_feature_handler selinux_hide_handler = {
    .feature_id = KSU_FEATURE_SELINUX_HIDE,
    .name = "selinux_hide",
    .get_handler = selinux_hide_feature_get,
    .set_handler = selinux_hide_feature_set,
};

void ksu_selinux_hide_handle_second_stage()
{
    initialize_fake_status();
    // https://github.com/torvalds/linux/blame/e8c2f9fdadee7cbc75134dc463c1e0d856d6e5c7/security/selinux/selinuxfs.c#L2014
    if (fake_status) {
#ifdef KSU_COMPAT_USE_STATIC_KEY
        static_key_disable(&fake_status_initialize_key.key);
#else
        fake_status_initialize_key = true;
#endif
    } else {
        pr_warn("selinux_hide: fake status need late initialization\n");
    }
}

void ksu_selinux_hide_handle_post_fs_data()
{
#ifdef KSU_COMPAT_USE_STATIC_KEY
    static_key_disable(&fake_status_initialize_key.key);
#else
    fake_status_initialize_key = true;
#endif
    if (!fake_status) {
        pr_err("selinux_hide: fake status is not initialized after post-fs-data!\n");
    }
}

void __init ksu_selinux_hide_init()
{
    if (ksu_register_feature_handler(&selinux_hide_handler)) {
        pr_err("Failed to register selinux_hide feature handler\n");
    }
    if (ksu_late_loaded) {
        initialize_fake_status();
    } else {
#ifdef KSU_COMPAT_USE_STATIC_KEY
        static_key_enable(&fake_status_initialize_key.key);
#else
        fake_status_initialize_key = false;
#endif
    }
#ifndef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE
    hook_selinux_status_open();
#endif
}

void __exit ksu_selinux_hide_exit()
{
    mutex_lock(&selinux_hide_mutex);
    if (ksu_selinux_hide_running) {
        ksu_selinux_hide_disable();
        ksu_selinux_hide_running = false;
    }
    mutex_unlock(&selinux_hide_mutex);
    ksu_unregister_feature_handler(KSU_FEATURE_SELINUX_HIDE);
    mutex_lock(ksu_selinux_status_lock);
    if (fake_status)
        __free_page(fake_status);
    fake_status = NULL;
    mutex_unlock(ksu_selinux_status_lock);
}

void ksu_selinux_hide_drop_backup_if_unused()
{
    mutex_lock(&selinux_hide_mutex);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    if (!ksu_selinux_hide_running && backup_sepolicy) {
        pr_info("selinux_hide is not enabled - drop backup_sepolicy\n");
        sidtab_destroy(backup_sepolicy->sidtab);
        kfree(backup_sepolicy->sidtab);
        ksu_destroy_sepolicy(backup_sepolicy);
        backup_sepolicy = NULL;
    }
#elif defined(KSU_COMPAT_USE_SELINUX_STATE)
    if (!ksu_selinux_hide_running && backup_policydb && backup_sidtab) {
        pr_info("selinux_hide is not enabled - drop backup_policydb\n");
        sidtab_destroy(backup_sidtab);
        kfree(backup_sidtab);
        ksu_destroy_policydb(backup_policydb);
        kfree(backup_policydb);
        backup_policydb = NULL;
        backup_sidtab = NULL;
    }
#else
    if (!ksu_selinux_hide_running && backup_policydb && backup_sidtab) {
        sidtab_destroy(backup_sidtab);
        kfree(backup_sidtab);
        ksu_destroy_policydb(backup_policydb);
        kfree(backup_policydb);
        backup_policydb = NULL;
        backup_sidtab = NULL;
    }
#endif
    mutex_unlock(&selinux_hide_mutex);
}

// for susfs xN
__maybe_static void initialize_fake_status()
{
    // https://github.com/torvalds/linux/commit/4b36cb773a8153417a080f8025d522322f915aea
#if LINUX_VERSION_CODE > KERNEL_VERSION(5, 7, 0) || defined(KSU_COMPAT_SELINUX_STATUS_VAR_IN_SELINUX_STATE)
    ksu_selinux_status_lock = &selinux_state.status_lock;
#elif defined(KSU_COMPAT_USE_SELINUX_STATE)
    ksu_selinux_status_lock = &selinux_state.ss->status_lock;
#elif defined(CONFIG_KALLSYMS_ALL)
    // call ksu_resolve_symbol_for_functable_hook to search selinux_status_lock
    // because some compiler add suffix for that
    // e.g:
    // 0000000000000000 b selinux_status_lock.llvm.9985633631847037644
    ksu_selinux_status_lock = (struct mutex *)ksu_resolve_symbol_for_functable_hook("selinux_status_lock");
#else
    extern struct mutex selinux_status_lock;
    ksu_selinux_status_lock = &selinux_status_lock;
#endif

    mutex_lock(ksu_selinux_status_lock);
    if (fake_status)
        goto out;

#if LINUX_VERSION_CODE > KERNEL_VERSION(5, 7, 0) || defined(KSU_COMPAT_SELINUX_STATUS_VAR_IN_SELINUX_STATE)
    struct page *selinux_status_page = selinux_state.status_page;
#elif defined(KSU_COMPAT_USE_SELINUX_STATE)
    struct page *selinux_status_page = selinux_state.ss->status_page;
#elif defined(CONFIG_KALLSYMS_ALL)
    // call ksu_resolve_symbol_for_functable_hook to search selinux_status_page
    // because some compiler add suffix for that
    // e.g:
    // 0000000000000000 b selinux_status_page.llvm.9985633631847037644
    struct page *selinux_status_page = *((struct page **)ksu_resolve_symbol_for_functable_hook("selinux_status_page"));
#else
    extern struct page *selinux_status_page;
#endif

    if (!selinux_status_page) {
        pr_warn("initialize_fake_status: status_page not exist\n");
        goto out;
    }

    struct selinux_kernel_status *status = page_address(selinux_status_page);
    if (!status->enforcing && !ksu_late_loaded) {
        pr_warn("initialize_fake_status: skip not enforcing\n");
        goto out;
    }

    struct page *new_page = alloc_page(GFP_KERNEL | __GFP_ZERO);
    if (!new_page) {
        pr_err("initialize_fake_status: failed to allocate page\n");
        goto out;
    }

    struct selinux_kernel_status *new_status = page_address(new_page);
    memcpy(new_status, status, sizeof(*status));
    if (ksu_late_loaded) {
        // In late_load mode the loader may have reloaded sepolicy before us,
        // so the captured page is not stock. Serve what a stock boot ends
        // with instead: creation sentinel below 6.10, one load plus one
        // setenforce above.
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 10, 0)
        new_status->sequence = 4;
        new_status->policyload = 1;
#else
        new_status->sequence = 0;
        new_status->policyload = 0;
#endif
        if (!new_status->enforcing) {
            new_status->enforcing = 1;
        }
    }

    fake_status = new_page;
    pr_info("initialize_fake_status initialized: sequence=%d, policyload=%d, enforcing=%d\n", new_status->sequence,
            new_status->policyload, new_status->enforcing);

out:
    mutex_unlock(ksu_selinux_status_lock);
}

/*
 * Caveat:  Mutates scontext.
 */
static int string_to_context_struct(struct policydb *pol, struct policydb *orig_pol, struct sidtab *sidtabp,
                                    struct sidtab *orig_sidtabp, char *scontext, char *orig_scontext, u32 scontext_len,
                                    struct context *ctx, struct context *orig_ctx, u32 def_sid, int *orig_rc_p)
{
    struct role_datum *role, *orig_role;
    struct type_datum *typdatum, *orig_typdatum;
    struct user_datum *usrdatum, *orig_usrdatum;
    char *scontextp, *orig_scontextp, *p, *orig_p, oldc, orig_oldc;
    int rc = 0, orig_rc = 0;

    context_init(ctx);
    context_init(orig_ctx);

    /* Parse the security context. */

    orig_rc = rc = -EINVAL;
    scontextp = scontext;
    orig_scontextp = orig_scontext;

    /* Extract the user. */
    p = scontextp;
    orig_p = orig_scontextp;
    while (*p && *p != ':') {
        p++;
        orig_p++;
    }

    if (*p == 0)
        goto out;

    *orig_p++ = *p++ = 0;

    usrdatum = symtab_search(&pol->p_users, scontextp);
    orig_usrdatum = symtab_search(&orig_pol->p_users, orig_scontextp);
    if (!usrdatum)
        goto out;

    ctx->user = usrdatum->value;
    if (orig_usrdatum)
        orig_ctx->user = orig_usrdatum->value;

    /* Extract role. */
    scontextp = p;
    orig_scontextp = orig_p;
    while (*p && *p != ':') {
        p++;
        orig_p++;
    }

    if (*p == 0)
        goto out;

    *orig_p++ = *p++ = 0;

    role = symtab_search(&pol->p_roles, scontextp);
    orig_role = symtab_search(&orig_pol->p_roles, orig_scontextp);
    if (!role)
        goto out;
    ctx->role = role->value;
    if (orig_role)
        orig_ctx->role = orig_role->value;

    /* Extract type. */
    scontextp = p;
    orig_scontextp = orig_p;
    while (*p && *p != ':') {
        p++;
        orig_p++;
    }
    oldc = *p;
    orig_oldc = *orig_p;
    *orig_p++ = *p++ = 0;

    typdatum = symtab_search(&pol->p_types, scontextp);
    orig_typdatum = symtab_search(&orig_pol->p_types, orig_scontextp);
    if (!typdatum || typdatum->attribute)
        goto out;

    ctx->type = typdatum->value;
    if (orig_typdatum && !orig_typdatum->attribute)
        orig_ctx->type = orig_typdatum->value;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 20, 0) || defined(KSU_COMPAT_HAS_STRICTER_MLS_CONTEXT_TO_SID)
    rc = mls_context_to_sid(pol, oldc, p, ctx, sidtabp, def_sid);
    orig_rc = mls_context_to_sid(orig_pol, orig_oldc, orig_p, orig_ctx, orig_sidtabp, def_sid);
#else
    rc = mls_context_to_sid(pol, oldc, &p, ctx, sidtabp, def_sid);
    if ((p - scontext) < scontext_len) {
        orig_rc = rc = -EINVAL;
        goto out;
    }

    orig_rc = mls_context_to_sid(orig_pol, orig_oldc, &orig_p, orig_ctx, orig_sidtabp, def_sid);
    if ((orig_p - orig_scontext) < scontext_len) {
        orig_rc = rc = -EINVAL;
        goto out;
    }
#endif
    if (rc) {
        orig_rc = -EINVAL;
        goto out;
    }

    /* Check the validity of the new context. */
    orig_rc = rc = -EINVAL;

    if (policydb_context_isvalid(pol, ctx)) {
        rc = 0;
    }
    if (policydb_context_isvalid(orig_pol, orig_ctx)) {
        orig_rc = 0;
    }
out:
    if (rc)
        context_destroy(ctx);
    if (orig_rc)
        context_destroy(orig_ctx);
    *orig_rc_p = orig_rc;
    return rc;
}

extern rwlock_t *ksu_policy_rwlock_ptr;

static inline void ksu_lock_sepolicy_legacy(void)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 10, 0) && !defined(KSU_COMPAT_HAS_POLICY_MUTEX)
// 4.14 - 5.10
#if defined(KSU_COMPAT_USE_SELINUX_STATE)
    read_lock(&selinux_state.ss->policy_rwlock);
// 4.14-
#else
    read_lock(ksu_policy_rwlock_ptr);
#endif
#endif
}

static inline void ksu_unlock_sepolicy_legacy(void)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 10, 0) && !defined(KSU_COMPAT_HAS_POLICY_MUTEX)
// 4.14 - 5.10
#if defined(KSU_COMPAT_USE_SELINUX_STATE)
    read_unlock(&selinux_state.ss->policy_rwlock);
// 4.14-
#else
    read_unlock(ksu_policy_rwlock_ptr);
#endif
#endif
}

// remove static in susfs
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
__maybe_static int security_context_to_sid_with_policy(struct selinux_policy *policy, const char *scontext,
                                                       u32 scontext_len, u32 *sid, u32 def_sid, gfp_t gfp_flags,
                                                       u32 *orig_sid_p, int *orig_rc_p)
#else
static int ksu_security_context_to_sid(struct policydb *orig_policydb, struct sidtab *orig_sidtab,
                                       struct policydb *policydb, struct sidtab *sidtab, const char *scontext,
                                       u32 scontext_len, u32 *sid, u32 def_sid, gfp_t gfp_flags, u32 *orig_sid_p,
                                       int *orig_rc_p)
#endif
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    struct selinux_policy *orig_policy;
    struct policydb *policydb, *orig_policydb;
    struct sidtab *sidtab, *orig_sidtab;
#endif
    char *scontext2, *scontext3, *str = NULL;
    struct context context, orig_context;
    int rc = 0, orig_rc = 0;
    u32 orig_sid;

    /* An empty security context is never valid. */
    if (!scontext_len)
        return -EINVAL;

    /* Copy the string to allow changes and ensure a NUL terminator */
    scontext2 = kmemdup_nul(scontext, scontext_len, gfp_flags);
    if (!scontext2)
        return -ENOMEM;

    scontext3 = kmemdup_nul(scontext, scontext_len, gfp_flags);
    if (!scontext3) {
        kfree(scontext2);
        return -ENOMEM;
    }

    // removed: if (!selinux_initialized())
    *sid = SECSID_NULL;
    if (orig_sid_p)
        *orig_sid_p = SECSID_NULL;

        // removed: if (force)
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    rcu_read_lock();
    orig_policy = rcu_dereference(selinux_state.policy);
    orig_policydb = &orig_policy->policydb;
    orig_sidtab = orig_policy->sidtab;
    policydb = &policy->policydb;
    sidtab = policy->sidtab;
#else
    ksu_lock_sepolicy_legacy();
#endif
    rc = string_to_context_struct(policydb, orig_policydb, sidtab, orig_sidtab, scontext2, scontext3, scontext_len,
                                  &context, &orig_context, def_sid, &orig_rc);
    if (!rc) {
        rc = sidtab_context_to_sid(sidtab, &context, sid);
        // rc should not be frozen
        context_destroy(&context);
        // removed: if (rc == -ESTALE)
        if (!orig_rc) {
            // sync to global sidtab
            orig_rc = sidtab_context_to_sid(orig_sidtab, &orig_context, &orig_sid);
            if (orig_sid_p)
                *orig_sid_p = orig_sid;
            context_destroy(&orig_context);
        }
    }
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    rcu_read_unlock();
#else
    ksu_unlock_sepolicy_legacy();
#endif
    if (orig_rc_p)
        *orig_rc_p = orig_rc;
    kfree(scontext3);
    kfree(scontext2);
    kfree(str);
    return rc;
}

/*
 * Write the security context string representation of
 * the context structure `context' into a dynamically
 * allocated string of the correct size.  Set `*scontext'
 * to point to this string and set `*scontext_len' to
 * the length of the string.
 */
static int context_struct_to_string(struct policydb *p, struct context *context, char **scontext, u32 *scontext_len)
{
    char *scontextp;

    if (scontext)
        *scontext = NULL;
    *scontext_len = 0;

    if (context->len) {
        *scontext_len = context->len;
        if (scontext) {
            *scontext = kstrdup(context->str, GFP_ATOMIC);
            if (!(*scontext))
                return -ENOMEM;
        }
        return 0;
    }

    /* Compute the size of the context. */
    *scontext_len += strlen(sym_name(p, SYM_USERS, context->user - 1)) + 1;
    *scontext_len += strlen(sym_name(p, SYM_ROLES, context->role - 1)) + 1;
    *scontext_len += strlen(sym_name(p, SYM_TYPES, context->type - 1)) + 1;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0) || defined(KSU_COMPAT_USE_SELINUX_STATE)
    *scontext_len += mls_compute_context_len(p, context);
#else
    *scontext_len += mls_compute_context_len(context);
#endif

    if (!scontext)
        return 0;

    /* Allocate space for the context; caller must free this space. */
    scontextp = kmalloc(*scontext_len, GFP_ATOMIC);
    if (!scontextp)
        return -ENOMEM;
    *scontext = scontextp;

    /*
     * Copy the user name, role name and type name into the context.
     */
    scontextp += sprintf(scontextp, "%s:%s:%s", sym_name(p, SYM_USERS, context->user - 1),
                         sym_name(p, SYM_ROLES, context->role - 1), sym_name(p, SYM_TYPES, context->type - 1));

#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0) || defined(KSU_COMPAT_USE_SELINUX_STATE)
    mls_sid_to_context(p, context, &scontextp);
#else
    mls_sid_to_context(context, &scontextp);
#endif

    *scontextp = 0;

    return 0;
}

static int ksu_security_sid_to_context(struct policydb *policydb, struct sidtab *sidtab, u32 sid, char **scontext,
                                       u32 *scontext_len)
{
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    struct sidtab_entry *entry;
#else
    struct context *context;
#endif
    int rc = 0;

    if (scontext)
        *scontext = NULL;
    *scontext_len = 0;

    // removed: if (!selinux_initialized())
    // removed: rcu lock

    // removed: force
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 10, 0) || defined(KSU_COMPAT_HAS_SELINUX_POLICY_STRUCT)
    entry = sidtab_search_entry(sidtab, sid);
    if (!entry) {
        pr_err("SELinux: %s:  unrecognized SID %d\n", __func__, sid);
        rc = -EINVAL;
        goto out;
    }
    // removed: only_invalid

    // rc = sidtab_entry_to_string(policydb, sidtab, entry, scontext, scontext_len);
    rc = sidtab_sid2str_get(sidtab, entry, scontext, scontext_len);
    if (rc != -ENOENT)
        goto out;

    rc = context_struct_to_string(policydb, &entry->context, scontext, scontext_len);

    if (!rc && scontext)
        sidtab_sid2str_put(sidtab, entry, *scontext, *scontext_len);
#else
    context = sidtab_search(sidtab, sid);
    if (!context) {
        printk(KERN_ERR "SELinux: %s:  unrecognized SID %d\n", __func__, sid);
        rc = -EINVAL;
        goto out;
    }
    rc = context_struct_to_string(policydb, context, scontext, scontext_len);
#endif

out:
    return rc;
}

static void avd_init(struct av_decision *avd)
{
    avd->allowed = 0;
    avd->auditallow = 0;
    avd->auditdeny = 0xffffffff;

    // hardcode 1 to avoid detect for "avdSeqNo"
    // Normal android only set selinux policy once,
    // So there can be simple hardcode to 1
    avd->seqno = 1;
    avd->flags = 0;
}

static void ksu_context_struct_compute_av_fallback(struct policydb *policydb, struct context *scontext,
                                                   struct context *tcontext, u16 tclass, struct av_decision *avd,
                                                   struct extended_perms *xperms);

/*
 * security_boundary_permission - drops violated permissions
 * on boundary constraint.
 */
static void __nocfi type_attribute_bounds_av(struct policydb *policydb, struct context *scontext,
                                             struct context *tcontext, u16 tclass, struct av_decision *avd)
{
    struct context lo_scontext;
    struct context lo_tcontext, *tcontextp = tcontext;
    struct av_decision lo_avd;
    struct type_datum *source;
    struct type_datum *target;
    u32 masked = 0;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0) || defined(KSU_COMPAT_HAS_MODERN_POLICYDB)
    source = policydb->type_val_to_struct[scontext->type - 1];
#else
    source = flex_array_get_ptr(policydb->type_val_to_struct_array, scontext->type - 1);
#endif
    BUG_ON(!source);

    if (!source->bounds)
        return;

#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0) || defined(KSU_COMPAT_HAS_MODERN_POLICYDB)
    target = policydb->type_val_to_struct[tcontext->type - 1];
#else
    target = flex_array_get_ptr(policydb->type_val_to_struct_array, tcontext->type - 1);
#endif
    BUG_ON(!target);

    memset(&lo_avd, 0, sizeof(lo_avd));

    memcpy(&lo_scontext, scontext, sizeof(lo_scontext));
    lo_scontext.type = source->bounds;

    if (target->bounds) {
        memcpy(&lo_tcontext, tcontext, sizeof(lo_tcontext));
        lo_tcontext.type = target->bounds;
        tcontextp = &lo_tcontext;
    }

    if (context_struct_compute_av_fn) {
        context_struct_compute_av_fn(policydb, scontext, tcontext, tclass, avd, NULL);
    } else {
        ksu_context_struct_compute_av_fallback(policydb, &lo_scontext, tcontextp, tclass, &lo_avd, NULL);
    }

    masked = ~lo_avd.allowed & avd->allowed;

    if (likely(!masked))
        return; /* no masked permission */

    /* mask violated permissions */
    avd->allowed &= ~masked;

    /* audit masked permissions */
    if (security_dump_masked_av_fn)
        security_dump_masked_av_fn(policydb, scontext, tcontext, tclass, masked, "bounds");
}

/*
 * Return the boolean value of a constraint expression
 * when it is applied to the specified source and target
 * security contexts.
 *
 * xcontext is a special beast...  It is used by the validatetrans rules
 * only.  For these rules, scontext is the context before the transition,
 * tcontext is the context after the transition, and xcontext is the context
 * of the process performing the transition.  All other callers of
 * constraint_expr_eval should pass in NULL for xcontext.
 */
static int constraint_expr_eval(struct policydb *policydb, struct context *scontext, struct context *tcontext,
                                struct context *xcontext, struct constraint_expr *cexpr)
{
    u32 val1, val2;
    struct context *c;
    struct role_datum *r1, *r2;
    struct mls_level *l1, *l2;
    struct constraint_expr *e;
    int s[CEXPR_MAXDEPTH];
    int sp = -1;

    for (e = cexpr; e; e = e->next) {
        switch (e->expr_type) {
        case CEXPR_NOT:
            BUG_ON(sp < 0);
            s[sp] = !s[sp];
            break;
        case CEXPR_AND:
            BUG_ON(sp < 1);
            sp--;
            s[sp] &= s[sp + 1];
            break;
        case CEXPR_OR:
            BUG_ON(sp < 1);
            sp--;
            s[sp] |= s[sp + 1];
            break;
        case CEXPR_ATTR:
            if (sp == (CEXPR_MAXDEPTH - 1))
                return 0;
            switch (e->attr) {
            case CEXPR_USER:
                val1 = scontext->user;
                val2 = tcontext->user;
                break;
            case CEXPR_TYPE:
                val1 = scontext->type;
                val2 = tcontext->type;
                break;
            case CEXPR_ROLE:
                val1 = scontext->role;
                val2 = tcontext->role;
                r1 = policydb->role_val_to_struct[val1 - 1];
                r2 = policydb->role_val_to_struct[val2 - 1];
                switch (e->op) {
                case CEXPR_DOM:
                    s[++sp] = ebitmap_get_bit(&r1->dominates, val2 - 1);
                    continue;
                case CEXPR_DOMBY:
                    s[++sp] = ebitmap_get_bit(&r2->dominates, val1 - 1);
                    continue;
                case CEXPR_INCOMP:
                    s[++sp] =
                        (!ebitmap_get_bit(&r1->dominates, val2 - 1) && !ebitmap_get_bit(&r2->dominates, val1 - 1));
                    continue;
                default:
                    break;
                }
                break;
            case CEXPR_L1L2:
                l1 = &(scontext->range.level[0]);
                l2 = &(tcontext->range.level[0]);
                goto mls_ops;
            case CEXPR_L1H2:
                l1 = &(scontext->range.level[0]);
                l2 = &(tcontext->range.level[1]);
                goto mls_ops;
            case CEXPR_H1L2:
                l1 = &(scontext->range.level[1]);
                l2 = &(tcontext->range.level[0]);
                goto mls_ops;
            case CEXPR_H1H2:
                l1 = &(scontext->range.level[1]);
                l2 = &(tcontext->range.level[1]);
                goto mls_ops;
            case CEXPR_L1H1:
                l1 = &(scontext->range.level[0]);
                l2 = &(scontext->range.level[1]);
                goto mls_ops;
            case CEXPR_L2H2:
                l1 = &(tcontext->range.level[0]);
                l2 = &(tcontext->range.level[1]);
                goto mls_ops;
            mls_ops:
                switch (e->op) {
                case CEXPR_EQ:
                    s[++sp] = mls_level_eq(l1, l2);
                    continue;
                case CEXPR_NEQ:
                    s[++sp] = !mls_level_eq(l1, l2);
                    continue;
                case CEXPR_DOM:
                    s[++sp] = mls_level_dom(l1, l2);
                    continue;
                case CEXPR_DOMBY:
                    s[++sp] = mls_level_dom(l2, l1);
                    continue;
                case CEXPR_INCOMP:
                    s[++sp] = mls_level_incomp(l2, l1);
                    continue;
                default:
                    BUG();
                    return 0;
                }
                break;
            default:
                BUG();
                return 0;
            }

            switch (e->op) {
            case CEXPR_EQ:
                s[++sp] = (val1 == val2);
                break;
            case CEXPR_NEQ:
                s[++sp] = (val1 != val2);
                break;
            default:
                BUG();
                return 0;
            }
            break;
        case CEXPR_NAMES:
            if (sp == (CEXPR_MAXDEPTH - 1))
                return 0;
            c = scontext;
            if (e->attr & CEXPR_TARGET)
                c = tcontext;
            else if (e->attr & CEXPR_XTARGET) {
                c = xcontext;
                if (!c) {
                    BUG();
                    return 0;
                }
            }
            if (e->attr & CEXPR_USER)
                val1 = c->user;
            else if (e->attr & CEXPR_ROLE)
                val1 = c->role;
            else if (e->attr & CEXPR_TYPE)
                val1 = c->type;
            else {
                BUG();
                return 0;
            }

            switch (e->op) {
            case CEXPR_EQ:
                s[++sp] = ebitmap_get_bit(&e->names, val1 - 1);
                break;
            case CEXPR_NEQ:
                s[++sp] = !ebitmap_get_bit(&e->names, val1 - 1);
                break;
            default:
                BUG();
                return 0;
            }
            break;
        default:
            BUG();
            return 0;
        }
    }

    BUG_ON(sp != 0);
    return s[0];
}

/*
 * Compute access vectors and extended permissions based on a context
 * structure pair for the permissions in a particular class.
 */
static void ksu_context_struct_compute_av_fallback(struct policydb *policydb, struct context *scontext,
                                                   struct context *tcontext, u16 tclass, struct av_decision *avd,
                                                   struct extended_perms *xperms)
{
    struct constraint_node *constraint;
    struct role_allow *ra;
    struct avtab_key avkey;
    struct avtab_node *node;
    struct class_datum *tclass_datum;
    struct ebitmap *sattr, *tattr;
    struct ebitmap_node *snode, *tnode;
    unsigned int i, j;

    avd->allowed = 0;
    avd->auditallow = 0;
    avd->auditdeny = 0xffffffff;
    if (xperms) {
        memset(&xperms->drivers, 0, sizeof(xperms->drivers));
        xperms->len = 0;
    }

    if (unlikely(!tclass || tclass > policydb->p_classes.nprim)) {
        pr_warn_ratelimited("SELinux:  Invalid class %u\n", tclass);
        return;
    }

    tclass_datum = policydb->class_val_to_struct[tclass - 1];

    /*
     * If a specific type enforcement rule was defined for
     * this permission check, then use it.
     */
    avkey.target_class = tclass;
    avkey.specified = AVTAB_AV | AVTAB_XPERMS;
#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0) ||                                                                   \
    (defined(KSU_COMPAT_HAS_MODERN_POLICYDB) && !defined(KSU_COMPAT_TYPE_ATTR_MAP_ARRAY_NOT_FOUND))
    sattr = &policydb->type_attr_map_array[scontext->type - 1];
    tattr = &policydb->type_attr_map_array[tcontext->type - 1];
#elif defined(KSU_COMPAT_TYPE_ATTR_MAP_ARRAY_NOT_FOUND)
    // huawei! why rename??!
    sattr = &policydb->type_attr_map[scontext->type - 1];
    tattr = &policydb->type_attr_map[tcontext->type - 1];
#else
    sattr = flex_array_get(policydb->type_attr_map_array, scontext->type - 1);
    BUG_ON(!sattr);
    tattr = flex_array_get(policydb->type_attr_map_array, tcontext->type - 1);
    BUG_ON(!tattr);
#endif
    ebitmap_for_each_positive_bit(sattr, snode, i)
    {
        ebitmap_for_each_positive_bit(tattr, tnode, j)
        {
            avkey.source_type = i + 1;
            avkey.target_type = j + 1;
            for (node = avtab_search_node(&policydb->te_avtab, &avkey); node;
                 node = avtab_search_node_next(node, avkey.specified)) {
                if (node->key.specified == AVTAB_ALLOWED)
                    avd->allowed |= node->datum.u.data;
                else if (node->key.specified == AVTAB_AUDITALLOW)
                    avd->auditallow |= node->datum.u.data;
                else if (node->key.specified == AVTAB_AUDITDENY)
                    avd->auditdeny &= node->datum.u.data;
                else if (xperms && (node->key.specified & AVTAB_XPERMS))
                    services_compute_xperms_drivers(xperms, node);
            }

            /* Check conditional av table for additional permissions */
            cond_compute_av(&policydb->te_cond_avtab, &avkey, avd, xperms);
        }
    }

    /*
     * Remove any permissions prohibited by a constraint (this includes
     * the MLS policy).
     */
    constraint = tclass_datum->constraints;
    while (constraint) {
        if ((constraint->permissions & (avd->allowed)) &&
            !constraint_expr_eval(policydb, scontext, tcontext, NULL, constraint->expr)) {
            avd->allowed &= ~(constraint->permissions);
        }
        constraint = constraint->next;
    }

    /*
     * If checking process transition permission and the
     * role is changing, then check the (current_role, new_role)
     * pair.
     */
    if (tclass == policydb->process_class && (avd->allowed & policydb->process_trans_perms) &&
        scontext->role != tcontext->role) {
        for (ra = policydb->role_allow; ra; ra = ra->next) {
            if (scontext->role == ra->role && tcontext->role == ra->new_role)
                break;
        }
        if (!ra)
            avd->allowed &= ~policydb->process_trans_perms;
    }

    /*
     * If the given source and target types have boundary
     * constraint, lazy checks have to mask any violated
     * permission and notice it to userspace via audit.
     */
    type_attribute_bounds_av(policydb, scontext, tcontext, tclass, avd);
}

// remove static in susfs
static __nocfi void ksu_security_compute_av_user(struct policydb *policydb, struct sidtab *sidtab, u32 ssid, u32 tsid,
                                                 u16 tclass, struct av_decision *avd)
{
    struct context *scontext = NULL, *tcontext = NULL;

    // remove: rcu lock
    avd_init(avd);
    // remove: if (!selinux_initialized())

    scontext = sidtab_search(sidtab, ssid);
    if (!scontext) {
        pr_err("SELinux: %s:  unrecognized SID %d\n", __func__, ssid);
        goto out;
    }

    /* permissive domain? */
    if (ebitmap_get_bit(&policydb->permissive_map, scontext->type))
        avd->flags |= AVD_FLAGS_PERMISSIVE;

    tcontext = sidtab_search(sidtab, tsid);
    if (!tcontext) {
        pr_err("SELinux: %s:  unrecognized SID %d\n", __func__, tsid);
        goto out;
    }

    if (unlikely(!tclass)) {
        if (policydb->allow_unknown)
            goto allow;
        goto out;
    }

    if (context_struct_compute_av_fn) {
        context_struct_compute_av_fn(policydb, scontext, tcontext, tclass, avd, NULL);
    } else {
        ksu_context_struct_compute_av_fallback(policydb, scontext, tcontext, tclass, avd, NULL);
    }
out:
    return;
allow:
    avd->allowed = 0xffffffff;
    goto out;
}
