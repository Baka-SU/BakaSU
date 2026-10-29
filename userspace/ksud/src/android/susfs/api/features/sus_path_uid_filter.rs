use anyhow::Result;

use super::package_list::validate_app_strings;

use crate::android::susfs::api::{
    magic::{CMD_SUSFS_SET_SUS_PATH_UID_FILTER, ERR_CMD_NOT_SUPPORTED, SUSFS_APP_STR_BUF_SIZE},
    susfsctl::{parse_err, susfsctl},
};

#[repr(C)]
struct SusfsSusPathUidFilter {
    app_strings: [u8; SUSFS_APP_STR_BUF_SIZE],
    err: i32,
}

pub fn set_sus_path_uid_filter(apps: &str) -> Result<()> {
    validate_app_strings(apps)?;
    // The upstream kernel rejects an empty string but accepts an empty comma-separated list.
    // A comma publishes an empty bitmap, allowing the last selected app to be removed.
    let apps = if apps.trim().is_empty() { "," } else { apps };
    let mut info = Box::new(SusfsSusPathUidFilter {
        app_strings: [0; SUSFS_APP_STR_BUF_SIZE],
        err: ERR_CMD_NOT_SUPPORTED,
    });
    info.app_strings[..apps.len()].copy_from_slice(apps.as_bytes());
    susfsctl(CMD_SUSFS_SET_SUS_PATH_UID_FILTER, &mut *info);
    parse_err(CMD_SUSFS_SET_SUS_PATH_UID_FILTER, info.err)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn matches_kernel_abi() {
        assert_eq!(std::mem::offset_of!(SusfsSusPathUidFilter, err), 65536);
        assert_eq!(std::mem::size_of::<SusfsSusPathUidFilter>(), 65540);
    }
}
