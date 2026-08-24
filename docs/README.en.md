# FTP Synchronization

[中文](../README.md) | [English](README.en.md) | [日本語](README.ja.md) | [한국어](README.ko.md)

FTP Synchronization is a JavaFX desktop application that uploads or downloads files between local and FTP directories on a schedule. Dynamic time-based directory rules make it suitable for data, images, messages, and device files.

## Features

- Multiple upload and download rules with add, view, edit, delete, enable, and disable operations.
- Year, month, day, hour, minute, and second path placeholders with minute offsets.
- Transfers only files missing from the destination.
- Per-rule or global latest-file count, file stability delay, FTP timeouts, and connection pooling.
- FTP connection test, system logs, and per-file transfer logs.
- System language, Chinese, English, Japanese, and Korean with immediate switching.
- Automated Windows, Linux, and macOS packages for x64 and ARM64.

## Download and run

Download the package matching your platform from GitHub Actions Artifacts or Releases:

| OS | x64 | ARM64 |
|---|---|---|
| Windows | `ftp-synchronization-<version>-windows-x64.zip` | `ftp-synchronization-<version>-windows-arm64.zip` |
| Linux | `ftp-synchronization-<version>-linux-x64.tar.gz` | `ftp-synchronization-<version>-linux-arm64.tar.gz` |
| macOS | `ftp-synchronization-<version>-macos-x64.tar.gz` | `ftp-synchronization-<version>-macos-arm64.tar.gz` |

No separate Java installation is required. Every package contains a Liberica Java 21 JRE with JavaFX. Run `run.bat`, `run.sh`, or `run.command` after extraction.

The application JAR contains the application plus non-JavaFX runtime dependencies. It contains no `javafx/**`, `com/sun/javafx/**`, or JavaFX native libraries; JavaFX exists only in `jre/`.

## Quick start

1. Open **FTP and Runtime Settings**.
2. Enter the host, port, username, and password, then test the connection.
3. Save the settings.
4. Add an upload or download rule.
5. Check the live directory preview and save the rule.
6. Select **Run Now** for the first verification.

Upload rules compare the latest N stable local files with FTP. Download rules compare the latest N FTP files with the local directory. Files already present at the destination are skipped.

## Rules and placeholders

A rule defines a name, direction, local root, remote root, dynamic path, minute offset, and optional latest-file count.

| Time | Not padded | Zero padded |
|---|---|---|
| Year | `{year}` | `{YEAR}` |
| Month | `{month}` | `{MONTH}` |
| Day | `{day}` | `{DAY}` |
| Hour | `{hh}` | `{HH}` |
| Minute | `{mm}` | `{MM}` |
| Second | `{ss}` | `{SS}` |

At `2026-08-01 03:05:09`, `{year}/{MONTH}/{DAY}/{HH}/{MM}/{SS}` resolves to `2026/08/01/03/05/09`. Legacy rules keep their previous case-insensitive padding behavior.

## Language and configuration

Select System, 中文, English, 日本語, or 한국어 in the settings page. The main view reloads immediately without restarting the scheduler or FTP pool.

- `pathRules.json` stores rules and remains backward compatible.
- `ftp-synchronization-settings.properties` stores FTP, schedule, and language settings.
- `application.properties` and `application.yml` remain supported.

When the new settings file is absent, an existing `ftp-upload-settings.properties` is loaded and copied to the new name. The old file is preserved for rollback.

## Build and test

Java 21 is required for source builds:

```bash
./mvnw clean verify
```

The command creates `target/ftp-synchronization.jar` and runs migration, localization, FXML, JSON compatibility, template, listener lifecycle, ProGuard, and no-JavaFX-in-JAR tests. CI additionally tests all six runtime packages.

See the [GitHub Actions packaging guide](GITHUB_ACTIONS_PACKAGING.zh-CN.md) for workflow maintenance details.

## Security

The FTP password is stored as plain text in the local settings file. Restrict file access and never publish passwords in screenshots, logs, commits, or issues. Verify downloaded archives using the included SHA-256 file.
