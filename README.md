# Minova Cloud for Android

A native Android app connected to the same Minova Cloud server as the website. The app supports Android 8.0 and newer. Its production origin is fixed to https://cloud.project-minova.com, with normal Android certificate validation and HTTPS required.

## Use

Download the signed APK from https://cloud.project-minova.com/android and install it on your phone. Sign in using the same Minova account as the website. New accounts are invitation-only; verified Android App Links open email invitations in the app when Android permits the association.

Files shows the full folder tree. Photos and Videos collect media from all your private folders. Browse, search, upload, preview, save a copy to your phone, create folders, rename, star, share with members, remove sharing access, move to trash, and restore. The More tab provides sharing, trash, connected devices, email invitations for the owner, backup settings, updates/help, and sign out. No Tailscale app is required.

Open library views refresh about every 10 seconds while the app is active; the website refreshes about every 15 seconds. Both clients use the same accounts, permission checks, files, shares, and trash. This is a shared live cloud library, with explicit downloads for separate local copies; it does not automatically mirror arbitrary phone folders or delete phone originals when cloud files are removed.

## Automatic backup

Backup is off by default. Open More > Automatic photo & video backup, grant Android media access, choose photos/videos and network settings, and enable it. Wi-Fi/unmetered networking is the default. Android schedules scans at 15-minute intervals when battery/network conditions permit, so this is not an instant camera watcher. Selected-media permissions limit backup to the media Android makes available. Android battery restrictions or force-stop can delay work; reopen the app after force-stop.

The first scan includes existing allowed media. Media goes to Phone backups/Android-<device>/Photos or Videos/YYYY-MM, preserving the original filename. Collisions receive a number and never overwrite another file. SQLite receipts skip already completed backups. If a transfer completed before its receipt was saved, a server-side SHA-256 check avoids a duplicate. A cloud deletion stays deleted until the user explicitly restores or uploads it again; backup does not delete phone originals.

Uploads are queued with WorkManager and resume after interruption while the server upload session remains available. Restarting the server or a session expiring after 30 minutes may restart the affected file. Files larger than 256 MB use Android's foreground transfer service. Every native upload declares a SHA-256 checksum, which the server verifies before publishing the file. The per-file limit is 20 GB. Android can stop background/foreground work according to its own quotas; the app retries eligible interrupted transfers.

## Authentication and privacy

The app exchanges a normal password sign-in for a device credential valid for 90 days. Passwords are not stored. The device credential is encrypted with an Android Keystore AES-GCM key and excluded from Android backups. The server stores only a hash. Credentials survive the server's weekly restart and can be revoked from Connected devices on either client. Expired/revoked credentials require a new sign-in. Each work request is bound to its originating Minova user, so a later account switch cannot send its uploads to another account.

The app has no analytics, ads, third-party accounts, broad all-files access, or shared-storage write permission. Manual uploads use the Android document picker. Camera/media backup requests Android's media permissions only when the user asks. Large transfers show a generic private notification without filenames. Cached previews are held in app-private cache and are exposed to a chosen external viewer only through temporary FileProvider permission.

## Build and update

Open this folder in Android Studio or run Build Minova Cloud.cmd. Build-Minova-Cloud.ps1 uses Android Studio's JDK and the Gradle wrapper. The SDK path is in ignored local.properties. AGP 9.2.0, Gradle 9.4.1, compile/target SDK 37, and AndroidX WorkManager 2.12.0 are pinned.

The release key is generated only once under %LOCALAPPDATA%/Minova Cloud/Android Signing. Its password is protected by Windows DPAPI for this Windows account. Back up that directory securely; losing the signing key prevents compatible updates. Never replace it for an update. Increase versionCode and versionName in app/build.gradle.kts for each new public APK, and update the release filename/metadata in Build-Minova-Cloud.ps1 and the server download page. The script never prints the password. Releases contains the signed APK, SHA256SUMS.txt, and public signing-certificate metadata.

The debug network config permits only localhost, 127.0.0.1, and 10.0.2.2 HTTP for isolated emulator tests; the release APK disallows cleartext. A debug-only origin preference is used by the integration test. Release code ignores that preference.

## Verification

The server's npm test suite checks private recursive media libraries, pagination, cross-account isolation, device token persistence/revocation, CSRF checks, hashed device storage, and checksum failures, along with existing file/sharing/email tests.

CloudIntegrationTest runs on an Android emulator against .qa/preview.mjs at port 4178 through adb reverse. It uses only synthetic local files and its own MediaStore test image. It checks Keystore storage, a 3 MiB interrupted upload and exact byte hash after resume, rename, a real WorkManager backup, receipt-loss recovery without duplicates, and cloud trash/restore visibility. The test account never touches I:/Online Cloud. Do not run this test against production; it deliberately uses a fixed synthetic test account and loopback origin.
