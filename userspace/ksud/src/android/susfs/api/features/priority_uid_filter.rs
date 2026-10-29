use anyhow::{Result, ensure};

use crate::android::susfs::api::{
    magic::{CMD_SUSFS_SET_PRIORITY_UID_FILTER, ERR_CMD_NOT_SUPPORTED, SUSFS_APP_STR_BUF_SIZE},
    susfsctl::{parse_err, susfsctl},
};

#[repr(C)]
struct SusfsPriorityUidFilter {
    app_strings: [u8; SUSFS_APP_STR_BUF_SIZE],
    err: i32,
}

pub fn validate_app_strings(apps: &str) -> Result<()> {
    ensure!(
        apps.len() < SUSFS_APP_STR_BUF_SIZE,
        "App package list exceeds 65534 bytes"
    );
    for name in apps.split(',').map(str::trim).filter(|s| !s.is_empty()) {
        ensure!(
            name.len() <= 255
                && name != "."
                && name != ".."
                && name
                    .bytes()
                    .all(|c| c.is_ascii_alphanumeric() || c == b'.' || c == b'_'),
            "Invalid app package name: {name}"
        );
    }
    Ok(())
}

pub fn set_priority_uid_filter(apps: &str) -> Result<()> {
    validate_app_strings(apps)?;
    // The upstream kernel rejects an empty string but accepts an empty comma-separated list.
    // A comma publishes an empty bitmap, allowing the last selected app to be removed.
    let apps = if apps.trim().is_empty() { "," } else { apps };
    let mut info = Box::new(SusfsPriorityUidFilter {
        app_strings: [0; SUSFS_APP_STR_BUF_SIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    });
    info.app_strings[..apps.len()].copy_from_slice(apps.as_bytes());
    susfsctl(CMD_SUSFS_SET_PRIORITY_UID_FILTER, &mut *info);
    parse_err(CMD_SUSFS_SET_PRIORITY_UID_FILTER, info.err)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_upstream_package_list_limits() {
        for apps in ["", ",", " com.example.app,org.example_app ,,"] {
            assert!(validate_app_strings(apps).is_ok());
        }
        for apps in ["..", "com/foo", "com.app\0evil", "com.app;id", "应用"] {
            assert!(validate_app_strings(apps).is_err());
        }
        assert!(validate_app_strings(&"a".repeat(256)).is_err());
        let max = format!("{}a", "a,".repeat(32766));
        assert!(validate_app_strings(&max).is_ok());
        assert!(validate_app_strings(&(max + ",a")).is_err());
    }

    #[test]
    fn matches_kernel_abi() {
        assert_eq!(std::mem::offset_of!(SusfsPriorityUidFilter, err), 65536);
        assert_eq!(std::mem::size_of::<SusfsPriorityUidFilter>(), 65540);
    }
}
