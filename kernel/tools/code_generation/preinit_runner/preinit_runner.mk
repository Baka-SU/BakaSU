# preinit runner: a static nolibc binary embedded into kernelsu, see preinit/.
# It is built against the installed UAPI headers of the target kernel
# (CONFIG_HEADERS_INSTALL, enabled by gki_defconfig).
KSU_UAPI_INC := $(objtree)/usr/include

ifeq ($(wildcard $(KSU_UAPI_INC)/linux/unistd.h),)
KSU_HEADERS_INSTALL_OUTPUT := $(shell $(MAKE) -s -C "$(abspath $(srctree))" \
	O="$(abspath $(objtree))" ARCH="$(ARCH)" \
	headers_install INSTALL_HDR_PATH="$(abspath $(objtree))/usr")
endif

ifneq ($(wildcard $(KSU_UAPI_INC)/linux/unistd.h),)
kernelsu-objs += preinit/preinit.o
kernelsu-objs += preinit/blob.o

KSU_RUNNER_OBJ := $(abspath $(obj))
KSU_RUNNER_BIN := $(KSU_RUNNER_OBJ)/preinit/runner.elf
KSU_RUNNER_TARGET := $(obj)/preinit/runner.elf
KSU_RUNNER_SRC := $(KSU_SRC)/preinit/runner

KSU_RUNNER_CFLAGS := $(CLANG_FLAGS) -std=gnu11 -Os -Wall \
	-nostdinc -isystem $(shell $(CC) -print-file-name=include) \
	-I$(KSU_RUNNER_SRC)/nolibc -I$(KSU_UAPI_INC) \
	-I$(KSU_SRC)/include \
	-ffreestanding -fno-stack-protector -fno-pic -fno-pie \
	-fno-asynchronous-unwind-tables -fno-unwind-tables \
	-ffunction-sections -fdata-sections

KSU_RUNNER_LIBS :=
ifeq ($(ARCH),arm)
KSU_RUNNER_LIBS := $(shell $(CC) $(CLANG_FLAGS) -print-libgcc-file-name)
endif

quiet_cmd_ksu_runner = CC      $@
      cmd_ksu_runner = mkdir -p $(dir $@) && \
		$(CC) $(KSU_RUNNER_CFLAGS) -c -o $@.o $< && \
		$(LD) -static -nostdlib -e _start --gc-sections -s -o $@ $@.o $(KSU_RUNNER_LIBS) && \
		rm -f $@.o

$(KSU_RUNNER_TARGET): $(KSU_RUNNER_SRC)/runner.c \
		$(wildcard $(KSU_RUNNER_SRC)/nolibc/*.h \
			   $(KSU_RUNNER_SRC)/nolibc/sys/*.h \
			   $(KSU_SRC)/include/uapi/*.h) FORCE
	$(call if_changed,ksu_runner)

targets += preinit/runner.elf
$(obj)/preinit/blob.o: $(KSU_RUNNER_TARGET)
asflags-y += -DKSU_PREINIT_RUNNER_BIN=\"$(KSU_RUNNER_BIN)\"
else
$(warning -- $(REPO_NAME): $(KSU_UAPI_INC) not found, preinit disabled)
ccflags-y += -DKSU_NO_PREINIT
endif

