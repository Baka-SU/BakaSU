/* SPDX-License-Identifier: LGPL-2.1 OR MIT */
/*
 * random definitions for NOLIBC
 * Copyright (C) 2025 Thomas Weißschuh <thomas.weissschuh@linutronix.de>
 */

/* make sure to include all global symbols */
#include "../nolibc.h"

#ifndef _NOLIBC_SYS_RANDOM_H
#define _NOLIBC_SYS_RANDOM_H

#include "../arch.h"
#include "../sys.h"

#include <linux/random.h>

/*
 * ssize_t getrandom(void *buf, size_t buflen, unsigned int flags);
 */

static __attribute__((unused))
ssize_t sys_getrandom(void *buf, size_t buflen, unsigned int flags)
{
#ifdef __NR_getrandom
	return my_syscall3(__NR_getrandom, buf, buflen, flags);
#else
	return __nolibc_enosys(__func__, buf, buflen, flags);
#endif
}

static __attribute__((unused))
ssize_t getrandom(void *buf, size_t buflen, unsigned int flags)
{
	return __sysret(sys_getrandom(buf, buflen, flags));
}

#endif /* _NOLIBC_SYS_RANDOM_H */
