//
// Created by shirkneko on 2025/11/3.
//
// Legacy Compatible
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <android/log.h>
#include <dirent.h>
#include <stdlib.h>
#include <limits.h>

#include "prelude.h"
#include "ksu.h"

#define KERNEL_SU_OPTION 0xDEADBEEF

#define CMD_GRANT_ROOT 0

#define CMD_BECOME_MANAGER 1
#define CMD_GET_VERSION 2

static bool ksuctl(int cmd, void* arg1, void* arg2) {
    int32_t result = 0;
    int32_t rtn = prctl(KERNEL_SU_OPTION, cmd, arg1, arg2, &result);
    return result == KERNEL_SU_OPTION && rtn == -1;
}

struct ksu_version_info legacy_get_info()
{
    int32_t version = -1;
    int32_t flags = 0;
    ksuctl(CMD_GET_VERSION, &version, &flags);
    return (struct ksu_version_info){version, flags};
}


