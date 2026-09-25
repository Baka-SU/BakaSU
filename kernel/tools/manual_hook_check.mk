define check_ksu_hook
  ifeq ($$(shell grep -q "$(1)" $(2); echo $$$$?),0)
      $$(info -- $$(REPO_NAME)/manual_hook: $(1) found)
  else
      $$(info -- You lost $(1) hook in your kernel)
      $$(info -- Read: https://resukisu.org/guide/manual-integrate.html)
      $$(error You should integrate $$(REPO_NAME) in your kernel. $(3))
  endif
endef

define check_ksu_hook_incompatible
  ifeq ($$(shell grep -q "$(1)" $(2); echo $$$$?),0)
      $$(info -- $(1) is incompatible hook)
      $$(info -- Read: https://resukisu.org/guide/manual-integrate.html)
      $$(error You should integrate $$(REPO_NAME) in your kernel correctly.)
  endif
endef

$(eval $(call check_ksu_hook_incompatible,ksu_vfs_read_hook,$(srctree)/fs/read_write.c))
$(eval $(call check_ksu_hook_incompatible,is_ksu_transition,$(srctree)/security/selinux/hooks.c))

ifeq ($(CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK), y)
  $(info -- $(REPO_NAME)/manual_hook: You are using LSM hooks for setuid hooks.)
  ifeq ($(shell test \( $(VERSION) -gt 6 -o \( $(VERSION) -eq 6 -a $(PATCHLEVEL) -ge 8 \) \) && echo y),y)
      $(info -- You can't use LSM hooks for kernel version >=6.8)
      $(info -- You should turn off CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK and hook setresuid manually)
      $(info -- Read: https://resukisu.org/guide/manual-integrate.html)
      $(error You can't use LSM hooks when kernel version >= 6.8)
  endif
else
  $(info -- $(REPO_NAME)/manual_hook: You are using a manual setresuid hook for setuid hooks.)
  $(eval $(call check_ksu_hook,ksu_handle_setresuid,$(srctree)/kernel/sys.c))
endif

ifeq ($(CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK), y)
  $(info -- $(REPO_NAME)/manual_hook: You are using LSM hooks for init rc hooks.)
  ifeq ($(shell test \( $(VERSION) -gt 6 -o \( $(VERSION) -eq 6 -a $(PATCHLEVEL) -ge 8 \) \) && echo y),y)
      $(info -- You can't use LSM hooks for kernel version >=6.8)
      $(info -- You should turn off CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK and hook sys_read manually.)
      $(info -- Read: https://resukisu.org/guide/manual-integrate.html)
      $(error You can't use LSM hooks when kernel version >= 6.8)
  endif
else
  $(info -- $(REPO_NAME)/manual_hook: You are using a manual sys_read hook for init rc hooks.)
  $(eval $(call check_ksu_hook,ksu_handle_sys_read,$(srctree)/fs/read_write.c))
endif

ifeq ($(CONFIG_KSU_MANUAL_HOOK_AUTO_INPUT_HOOK), y)
  $(info -- $(REPO_NAME)/manual_hook: You are using input_handler for input hooks.)
else
  $(eval $(call check_ksu_hook,ksu_handle_input_handle_event,$(srctree)/drivers/input/input.c))
endif

  ifeq ($(shell grep -q "ksu_handle_execveat" $(srctree)/fs/exec.c; echo $$?),0)
    $(info -- $(REPO_NAME)/manual_hook: ksu_handle_execveat found)
  else
    ifeq ($(shell test \( $(VERSION) -lt 3 -o \( $(VERSION) -eq 3 -a $(PATCHLEVEL) -le 14 \) \) && echo y),y)
      # https://github.com/torvalds/linux/commit/c4ad8f98bef77c7356aa6a9ad9188a6acc6b849d
      # when 3.14-, we should use ksu_handle_execve 
      ifeq ($(shell grep -q "ksu_handle_execve" $(srctree)/fs/exec.c; echo $$?),0)
        $(info -- $(REPO_NAME)/manual_hook: ksu_handle_execve found)
      else
        $(info -- You lost ksu_handle_execve hook in your kernel)
        $(info -- Read: https://resukisu.org/guide/manual-integrate.html)
        $(error You should integrate $(REPO_NAME) in your kernel.)
      endif
    else
      $(info -- You lost ksu_handle_execveat hook in your kernel)
      $(info -- Read: https://resukisu.org/guide/manual-integrate.html)
      $(error You should integrate $(REPO_NAME) in your kernel.)
    endif
  endif

$(eval $(call check_ksu_hook,ksu_handle_faccessat,$(srctree)/fs/open.c))
$(eval $(call check_ksu_hook,ksu_handle_stat,$(srctree)/fs/stat.c))
$(eval $(call check_ksu_hook,ksu_handle_newfstat_ret,$(srctree)/fs/stat.c))
$(eval $(call check_ksu_hook,ksu_handle_fstat64_ret,$(srctree)/fs/stat.c))

ifeq ($(shell test \( $(VERSION) -lt 3 -o \( $(VERSION) -eq 3 -a $(PATCHLEVEL) -le 11 \) \) && echo y),y)
  # https://github.com/torvalds/linux/commit/15d94b82565ebfb0cf27830b96e6cf5ed2d12a9a
  # when 3.11-, it maybe in kernel/sys.c
  $(eval $(call check_ksu_hook,ksu_handle_sys_reboot,$(srctree)/kernel/sys.c))
else
  # when 3.12+, it is in kernel/reboot.c
  $(eval $(call check_ksu_hook,ksu_handle_sys_reboot,$(srctree)/kernel/reboot.c))
endif

# we no need this hook, because we can directly replace selinux_ops to LSM hook in UL
$(eval $(call check_ksu_hook_incompatible,ksu_handle_rename,$(srctree)/security/security.c))

# opt ksu_key_permission, i think it are no need for UL
