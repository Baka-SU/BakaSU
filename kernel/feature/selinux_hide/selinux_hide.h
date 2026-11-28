#ifndef __KSU_H_SELINUX_HIDE
#define __KSU_H_SELINUX_HIDE
#include <linux/types.h>
#include <linux/version.h>
#include <linux/sched.h>
#include <linux/fs.h>

void ksu_selinux_hide_init();
void ksu_selinux_hide_exit();
void ksu_selinux_hide_drop_backup_if_unused();
void ksu_selinux_hide_handle_second_stage();
void ksu_selinux_hide_handle_post_fs_data();

// https://github.com/torvalds/linux/commit/b21507e272627c434e8dd74e8d51fd8245281b59
#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0) || defined(KSU_COMPAT_SETPROCATTR_USE_NEW_PROTOTYPE)
typedef int (*setprocattr_fn)(const char *name, void *value, size_t size);

int ksu_handle_selinux_setprocattr(const char *name, void *value, size_t size);
#else
typedef int (*setprocattr_fn)(struct task_struct *p, char *name, void *value, size_t size);

int ksu_handle_selinux_setprocattr(struct task_struct *p, char *name, void *value, size_t size);
#endif

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 3, 0) || defined(KSU_COMPAT_USE_6_3_INODE_SETXAATR)
typedef int (*inode_setxattr_fn)(struct mnt_idmap *idmap, struct dentry *dentry, const char *name, const void *value,
                                 size_t size, int flags);
int ksu_handle_selinux_inode_setxattr(struct mnt_idmap *idmap, struct dentry *dentry, const char *name,
                                      const void *value, size_t size, int flags);
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(5, 12, 0) || defined(KSU_COMPAT_USE_5_12_INODE_SETXAATR)
typedef int (*inode_setxattr_fn)(struct user_namespace *mnt_userns, struct dentry *dentry, const char *name,
                                 const void *value, size_t size, int flags);
int ksu_handle_selinux_inode_setxattr(struct user_namespace *mnt_userns, struct dentry *dentry, const char *name,
                                      const void *value, size_t size, int flags);
#else
typedef int (*inode_setxattr_fn)(struct dentry *dentry, const char *name, const void *value, size_t size, int flags);

int ksu_handle_selinux_inode_setxattr(struct dentry *dentry, const char *name, const void *value, size_t size,
                                      int flags);
#endif

#endif
