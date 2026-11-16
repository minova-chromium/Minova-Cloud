
Validation completed for 1.0.0:
- Six Node server suites passed, including private libraries, device tokens, checksum validation, sharing, and email transport isolation.
- Two Android instrumentation tests passed on API 37: byte-identical resumable upload, WorkManager backup and deduplication after lost receipt, rename/trash/restore synchronization, Keystore storage, and photo EXIF orientation.
- Signed release APK verified with apksigner.
- Public APK download SHA-256 matched the local release.
- Android verified the cloud.project-minova.com App Links association for the production app certificate.
- Signed production app installed in the emulator and left at the Minova sign-in screen. No production user credentials were entered by the assistant.
