# Android 1.2.0 validation

Eight instrumentation tests passed on an isolated Android API 37 emulator using a fresh synthetic Minova server on port 4178:

- Photo EXIF rotation is respected.
- Existing encrypted credentials, interrupted upload recovery, checksum matching, automatic backup, receipt-loss deduplication, rename, trash, and restore work.
- The new phone gallery and shared-file browser load under the Android activity lifecycle.
- The cleanup worker uploads without removing originals. A valid Android deletion request can be prepared only after verification; merely preparing it does not delete anything. The cloud copy remains after removal of the test-owned phone fixture.
- Altered or missing cloud copies block cleanup.
- Changed phone originals, cancelled batches (including cancellation after verification), foreign accounts, and non-MediaStore URIs cannot authorize removal.
- A real 21 MB video backs up into Videos and verifies without removing its phone original.
- With all-files access granted to the debug app, the shared-storage browser uploads a selected file and keeps the phone original.

The original two integration tests are included in this count. Debug compilation and Android lint passed with zero errors. The scoped-storage policy warning is expected for the explicitly requested optional all-files access; existing nonfatal lint warnings remain.

Only synthetic fixtures were used. No real user credentials were entered, no production storage was used by the tests, and no Android deletion-consent dialog was accepted automatically. An entirely separate headless QA emulator was used; the user's emulator profile was left unchanged.

Reproducing the full suite requires the sibling Minova server checkout, `node test/fixture.mjs`, `adb reverse tcp:4178 tcp:4178`, debug photo/video permissions and debug-only all-files access, plus a nonpersonal MP4 copied to ignored `app/src/androidTest/assets/qa-video.mp4`. The video fixture is not included in release APKs or Git history. The test package is `com.minova.cloud.debug.test`, targeting `com.minova.cloud.debug`.

Protected cleanup requires Android 11 or newer. Android controls full/selected photo access, shared-file access, and the final media-removal request. Minova also asks for explicit confirmation after fresh SHA-256 verification. Background workers never delete phone originals; revocation, verification failures, cancellation, or missing files keep removal blocked.
