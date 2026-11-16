# Minova Cloud

[![Build Minova Cloud](https://github.com/minova-chromium/Minova-Cloud/actions/workflows/android.yml/badge.svg)](https://github.com/minova-chromium/Minova-Cloud/actions/workflows/android.yml)

Your files, photos, and videos, together on Android and the web. A native Android app with the Minova graphite and cyan identity, private sharing, queued uploads, and opt-in photo/video backup.

**[Download the latest stable APK](https://github.com/minova-chromium/Minova-Cloud/releases/latest/download/Minova-Cloud.apk)** · **[All releases](https://github.com/minova-chromium/Minova-Cloud/releases)** · **[Website](https://cloud.project-minova.com/android)**

Android 8.0 or newer. Install over your existing Minova Cloud app to retain your account and settings. Sign in with your existing Minova username and password.

## Release workflow

1. Push a change to `main`. GitHub automatically compiles and checks the app, keeps a preview APK, and prepares a signed draft release for a new app version.
2. Review the APK in **Actions** or **Releases**. Drafts stay off the website.
3. Publish the draft in GitHub Releases. The website's download button immediately follows GitHub's latest stable release.

For a completely automatic publication, push a matching `vX.Y.Z` tag or run **Build Minova Cloud** with **Publish this version after checks pass** selected. The workflow uploads APKs and checksums before marking the release public, so the latest link does not point to an unfinished build.

Before a new version, increase **both** `versionCode` and `versionName` in `app/build.gradle.kts`. Published releases are retained and never overwritten by the workflow. If the current version is already published, a push still produces a preview artifact but requires a version increase before another draft can be created. Pull requests compile and run lint without access to the release signing secrets.

Every stable release includes `Minova-Cloud.apk`, a versioned APK, `SHA256SUMS.txt`, and public `release.json` metadata. The unversioned asset name is intentional: GitHub's `/releases/latest/download/Minova-Cloud.apk` URL always resolves to the current stable APK. Older builds remain in GitHub's release history.

## What stays synchronized

Files, Photos, Videos, folders, names, stars, shares, and trash use the same account and storage as the website. Open Android views refresh about every 10 seconds; the website refreshes about every 15 seconds. Downloads create explicit local copies; the app does not automatically mirror arbitrary phone folders or remove phone originals when cloud files are deleted.

## Phone backup

Backup is off by default. Open **More → Photo & video backup**, grant media access, choose Photos/Videos and network preferences, then enable it. Unmetered/Wi-Fi networking is the default. Android schedules scans at 15-minute intervals when battery/network conditions allow it; selected-media permissions restrict which items are available. Reopen the app after force-stop.

The first scan includes existing allowed media. Backups preserve original filenames under `Phone backups/Android-<device>/Photos` or `Videos/YYYY-MM`. Collisions are numbered without overwriting. SQLite receipts and SHA-256 checks avoid duplicate recovery uploads. Queued transfers resume when possible; large files use Android's foreground transfer service and remain subject to Android's background quotas. The per-file limit is 20 GB.

## Privacy and device access

Passwords are not stored. A normal sign-in creates a 90-day app credential encrypted with Android Keystore AES-GCM. The server stores only its hash. This credential survives server restarts and can be revoked from Connected devices on either client. Work requests are bound to the originating Minova user. The app has no ads or analytics and requests media access only for backup; manual uploads use Android's document picker.

The production origin is fixed to `https://cloud.project-minova.com` with normal certificate validation and HTTPS required. The debug-only loopback origin is for isolated emulator tests and is ignored by release builds. Debug builds use a separate package so testing cannot replace a signed production installation.

## Local builds and signing

Open this folder in Android Studio or run `Build Minova Cloud.cmd`. The PowerShell build script reads the version from Gradle. Keep `local.properties` private for your SDK path. The Gradle wrapper and dependency versions are pinned.

The existing production signing key is retained under `%LOCALAPPDATA%/Minova Cloud/Android Signing`; its password is protected with Windows DPAPI. Back it up securely. GitHub builds use the same key through encrypted Actions secrets `MINOVA_KEYSTORE_BASE64` and `MINOVA_KEYSTORE_PASSWORD`. The key and password are never committed or attached to release artifacts. `signing-certificate.sha256` contains only the public certificate fingerprint for verification.

The server itself, account data, user files, Gmail credentials, and deployment configuration remain on the owner's PC. This repository contains the Android client and its build/release workflow.

## Validation

GitHub runs Android compilation, lint, signature checks, and release packaging. `CloudIntegrationTest` is an optional emulator test against an isolated Minova server fixture on port 4178 through `adb reverse`; it uses a fixed synthetic account and never targets production. It covers Keystore storage, interrupted upload recovery with matching SHA-256, backup deduplication after receipt loss, rename/trash/restore visibility, and photo orientation. Do not point that test at a live user account.
