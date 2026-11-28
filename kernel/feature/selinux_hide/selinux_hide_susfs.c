// WARN: NEVER compile standalone
// This file MUST be include by selinux_hide.c !!!

#include <linux/cred.h>
#include <linux/fs.h>

#include "hook/patch_memory.h"

typedef ssize_t (*write_op_fn)(struct file *, char *, size_t);

static write_op_fn *selinux_write_op;

static write_op_fn *access_write;
static write_op_fn orig_access_write;

// This hook is susfs's avd_seqno return value fix
// Remove it when a day susfs picked upstream's latest_granting update commit
// WARN: hack return value
static ssize_t my_write_access(struct file *file, char *buf, size_t size)
{
    ssize_t len = orig_access_write(file, buf, size);
    char *seqno = buf;
    char *end, *seqno_end;
    int i;

    if (len <= 0 || len >= SIMPLE_TRANSACTION_LIMIT || current_uid().val < 10000 || !ksu_selinux_hide_running)
        return len;

    // length = scnprintf(buf, SIMPLE_TRANSACTION_LIMIT,
    //                    "%x %x %x %x %u %x",
    //                    avd.allowed, 0xffffffff,
    //                    avd.auditallow, avd.auditdeny,
    //                    avd.seqno, avd.flags);
    // avd.seqno is the 5th field, after 4 spaces
    // sscanf is too slow, let's make it speed up
    end = buf + len;
    for (i = 0; i < 4; i++) {
        seqno = memchr(seqno, ' ', end - seqno);
        if (!seqno)
            return len;
        seqno++;
    }

    seqno_end = seqno;
    while (seqno_end < end && *seqno_end >= '0' && *seqno_end <= '9')
        seqno_end++;
    if (seqno_end == seqno || seqno_end == end || *seqno_end != ' ')
        return len;

    // replace garbages to 1
    *seqno = '1';
    memmove(seqno + 1, seqno_end, end - seqno_end);
    len -= seqno_end - (seqno + 1);
    buf[len] = '\0';
    return len;
}

static void ksu_selinux_hide_prepare(void)
{
}

static int ksu_selinux_hide_hook(void)
{
    int ret;

    if (access_write)
        return 0;

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

    access_write = &selinux_write_op[SEL_ACCESS];
    pr_info("selinux_hide: access_write: 0x%lx [%pSb]\n", (unsigned long)*access_write, *access_write);
    write_op_fn my = my_write_access;
    orig_access_write = *access_write;
    ret = ksu_patch_text(access_write, &my, sizeof(my), KSU_PATCH_TEXT_FLUSH_DCACHE);
    if (ret) {
        pr_err("selinux_hide: init: patch_text access_write err: %d\n", ret);
        access_write = NULL;
        return ret;
    }

    return 0;
}

static void ksu_selinux_hide_unhook(void)
{
    int ret;

    if (!access_write)
        return;

    ret = ksu_patch_text(access_write, &orig_access_write, sizeof(orig_access_write), KSU_PATCH_TEXT_FLUSH_DCACHE);
    if (ret) {
        pr_err("selinux_hide: exit: patch_text access_write err: %d\n", ret);
        return;
    }
    access_write = NULL;
}

static bool ksu_selinux_hide_backup_in_use(void)
{
    return ksu_selinux_hide_running;
}
