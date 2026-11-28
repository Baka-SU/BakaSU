#include "selinux_hide.h"
#include <linux/init.h>
#include <linux/jump_label.h>
#include <linux/memory.h>
#include <linux/mutex.h>
#include <linux/printk.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/version.h>

#include <security.h>
#include <ss/services.h>

#include "compat/kernel_compat.h"
#include "infra/symbol_resolver.h"
#include "klog.h" // IWYU pragma: keep
#include "ksu.h"
#include "policy/feature.h"
#include "selinux/selinux.h"
#include "selinux/sepolicy.h"

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

#ifdef KSU_COMPAT_HAS_SUSFS_FEATURE_SELINUX_HIDE
#include "selinux_hide_susfs.c"
#else
#include "selinux_hide_auto.c"
#endif

static int ksu_selinux_hide_enable()
{
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

    ksu_selinux_hide_prepare();

    return ksu_selinux_hide_hook();
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
    ksu_selinux_hide_prepare();
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
    if (!ksu_selinux_hide_backup_in_use() && backup_sepolicy) {
        pr_info("selinux_hide is not enabled - drop backup_sepolicy\n");
        sidtab_destroy(backup_sepolicy->sidtab);
        kfree(backup_sepolicy->sidtab);
        ksu_destroy_sepolicy(backup_sepolicy);
        backup_sepolicy = NULL;
    }
#elif defined(KSU_COMPAT_USE_SELINUX_STATE)
    if (!ksu_selinux_hide_backup_in_use() && backup_policydb && backup_sidtab) {
        pr_info("selinux_hide is not enabled - drop backup_policydb\n");
        sidtab_destroy(backup_sidtab);
        kfree(backup_sidtab);
        ksu_destroy_policydb(backup_policydb);
        kfree(backup_policydb);
        backup_policydb = NULL;
        backup_sidtab = NULL;
    }
#else
    if (!ksu_selinux_hide_backup_in_use() && backup_policydb && backup_sidtab) {
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
