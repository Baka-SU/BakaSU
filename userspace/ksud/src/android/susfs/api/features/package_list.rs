use anyhow::{Result, ensure};

use crate::android::susfs::api::magic::SUSFS_APP_STR_BUF_SIZE;

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
