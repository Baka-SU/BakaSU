/* SPDX-License-Identifier: LGPL-2.1 OR MIT */
/*
 * stat definition for NOLIBC
 * Copyright (C) 2017-2021 Willy Tarreau <w@1wt.eu>
 */

/* make sure to include all global symbols */
#include "../nolibc.h"

#ifndef _NOLIBC_SYS_STAT_H
#define _NOLIBC_SYS_STAT_H

#include "../arch.h"
#include "../types.h"
#include "../sys.h"

#ifndef STATX_BASIC_STATS
// asm/stat declear struct stat in older kernel
#define stat __nolibc_kernel_stat
#include <asm/stat.h>
#undef stat
#endif

struct statx;

/*
 * int statx(int fd, const char *path, int flags, unsigned int mask, struct statx *buf);
 * int stat(const char *path, struct stat *buf);
 * int fstatat(int fd, const char *path, struct stat *buf, int flag);
 * int fstat(int fildes, struct stat *buf);
 * int lstat(const char *path, struct stat *buf);
 */

static __attribute__((unused))
int sys_statx(int fd, const char *path, int flags, unsigned int mask, struct statx *buf)
{
#ifdef __NR_statx
	return my_syscall5(__NR_statx, fd, path, flags, mask, buf);
#else
	return __nolibc_enosys(__func__, fd, path, flags, mask, buf);
#endif
}

static __attribute__((unused))
int statx(int fd, const char *path, int flags, unsigned int mask, struct statx *buf)
{
	return __sysret(sys_statx(fd, path, flags, mask, buf));
}


static __attribute__((unused))
int fstatat(int fd, const char *path, struct stat *buf, int flag)
{
#ifdef STATX_BASIC_STATS
	struct statx statx;
	long ret;

	ret = __sysret(sys_statx(fd, path, flag | AT_NO_AUTOMOUNT, STATX_BASIC_STATS, &statx));
	if (ret == -1)
		return ret;

	buf->st_dev          = ((statx.stx_dev_minor & 0xff)
			       | (statx.stx_dev_major << 8)
			       | ((statx.stx_dev_minor & ~0xff) << 12));
	buf->st_ino          = statx.stx_ino;
	buf->st_mode         = statx.stx_mode;
	buf->st_nlink        = statx.stx_nlink;
	buf->st_uid          = statx.stx_uid;
	buf->st_gid          = statx.stx_gid;
	buf->st_rdev         = ((statx.stx_rdev_minor & 0xff)
			       | (statx.stx_rdev_major << 8)
			       | ((statx.stx_rdev_minor & ~0xff) << 12));
	buf->st_size         = statx.stx_size;
	buf->st_blksize      = statx.stx_blksize;
	buf->st_blocks       = statx.stx_blocks;
	buf->st_atim.tv_sec  = statx.stx_atime.tv_sec;
	buf->st_atim.tv_nsec = statx.stx_atime.tv_nsec;
	buf->st_mtim.tv_sec  = statx.stx_mtime.tv_sec;
	buf->st_mtim.tv_nsec = statx.stx_mtime.tv_nsec;
	buf->st_ctim.tv_sec  = statx.stx_ctime.tv_sec;
	buf->st_ctim.tv_nsec = statx.stx_ctime.tv_nsec;
#else
#if defined(__NR_newfstatat)
	struct __nolibc_kernel_stat kstat;
	long ret = my_syscall4(__NR_newfstatat, fd, path, &kstat, flag);
#elif defined(__NR_fstatat64)
	struct stat64 kstat;
	long ret = my_syscall4(__NR_fstatat64, fd, path, &kstat, flag);
#else
	return __sysret(__nolibc_enosys(__func__, fd, path, buf, flag));
#endif
#if defined(__NR_newfstatat) || defined(__NR_fstatat64)
	if (ret < 0)
		return __sysret(ret);

	buf->st_dev          = kstat.st_dev;
	buf->st_ino          = kstat.st_ino;
	buf->st_mode         = kstat.st_mode;
	buf->st_nlink        = kstat.st_nlink;
	buf->st_uid          = kstat.st_uid;
	buf->st_gid          = kstat.st_gid;
	buf->st_rdev         = kstat.st_rdev;
	buf->st_size         = kstat.st_size;
	buf->st_blksize      = kstat.st_blksize;
	buf->st_blocks       = kstat.st_blocks;
	buf->st_atim.tv_sec  = kstat.st_atime;
	buf->st_atim.tv_nsec = kstat.st_atime_nsec;
	buf->st_mtim.tv_sec  = kstat.st_mtime;
	buf->st_mtim.tv_nsec = kstat.st_mtime_nsec;
	buf->st_ctim.tv_sec  = kstat.st_ctime;
	buf->st_ctim.tv_nsec = kstat.st_ctime_nsec;
#endif
#endif

	return 0;
}

static __attribute__((unused))
int stat(const char *path, struct stat *buf)
{
	return fstatat(AT_FDCWD, path, buf, 0);
}

static __attribute__((unused))
int fstat(int fildes, struct stat *buf)
{
	return fstatat(fildes, "", buf, AT_EMPTY_PATH);
}

static __attribute__((unused))
int lstat(const char *path, struct stat *buf)
{
	return fstatat(AT_FDCWD, path, buf, AT_SYMLINK_NOFOLLOW);
}

#endif /* _NOLIBC_SYS_STAT_H */
