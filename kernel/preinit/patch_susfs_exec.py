#!/usr/bin/env python3
'''Conservative downstream fs/exec.c adapter: run in the LINUX KERNEL root.

Only accepts the known SUSFS preinit hook layout (orig_flow, preinit_fd,
is_ksu_preinit_session, and post-exec callback). It refuses unknown trees.
Requires the original SUSFS patch already applied.
'''
from pathlib import Path

path = Path("fs/exec.c")
src = path.read_text()
required = ("ksu_setup_preinit_file(", "ksu_preinit_handle_post_execveat(",
            "orig_flow:", "is_ksu_preinit_session", "preinit_fd")
missing = [x for x in required if x not in src]
if missing:
    raise SystemExit(f"Cannot adapt fs/exec.c; missing SUSFS anchors: {missing}")

# Interception must be cleaned up on *every* early-error exit, not only after
# bprm_execve(). The handler restores the original filename for normal init.
anchor = "out_ret:\n\tputname(filename);\n\treturn retval;"
if anchor not in src:
    anchor = "out_ret:\n    putname(filename);\n    return retval;"
if anchor not in src:
    raise SystemExit("Unknown out_ret layout: refusing unsafe edit")
cleanup = '''out_ret:
#ifdef CONFIG_KSU_SUSFS
#ifndef KSU_NO_PREINIT
    if (unlikely(is_ksu_preinit_session)) {
        if (ksu_preinit_handle_post_execveat(
                &fd, &filename, &argv, &envp,
                &flags, &retval, &preinit_fd)) {
            is_ksu_preinit_session = false;
            goto orig_flow;
        }
        is_ksu_preinit_session = false;
    }
#endif
#endif
    putname(filename);
    return retval;'''
if "preinit: runner exec failed" in src or "early-error preinit cleanup" in src:
    raise SystemExit("Appears already patched")
cleanup = cleanup.replace("out_ret:\n", "out_ret:\n    /* early-error preinit cleanup */\n", 1)
src = src.replace(anchor, cleanup, 1)
path.write_text(src)
print("Updated fs/exec.c early-error cleanup; review the diff before building")
