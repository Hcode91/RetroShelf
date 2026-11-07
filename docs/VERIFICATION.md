# Verification record

## Live sources — 2026-11-07

Executed `python tools/provider-smoke.py` with normal public HTTPS access:

```json
{
  "universal_games": 155,
  "gba_manifests": 251,
  "gba_games": 240,
  "approved_download_bytes": 136500,
  "artwork": "verified for both sources"
}
```

The check reads all GBA manifests, verifies sample artwork on both providers, reads the reviewed game's upstream MIT license, and downloads the exact 2048 release file to verify its advertised size. It does not install or execute it. No credentials or access bypasses are used. The check is optional and excluded from ordinary CI tests.

## Automated checks

- `android-debug.yml`: deterministic JUnit/MockWebServer fixture tests, Android lint, debug APK artifact.
- `android-device.yml`: Android 35 emulator tests covering native navigation, unsupported console state, portrait/landscape rendering, actual Room favorite persistence across database reopening, details after activity recreation and download history empty state. Screenshots and instrumentation reports are uploaded as artifacts. Device fixtures are synthetic test-only records and never appear in production builds.

## Remaining manual checks

Real-device DownloadManager notification/progress/cancel/retry and SAF export depend on Android system services and user interaction. These should be checked on hardware; unit tests and the offline emulator smoke test do not prove live download behavior. The live-source check proves availability of the reviewed binary, not emulator execution of downloaded game software.

## GameBrew addition — 2026-11-07

The live public MediaWiki parse response was processed by the actual Kotlin/Jsoup adapter: **720 unique GBA games, 15 genres, 720 gallery artwork URLs**. The Minicraft GBA detail response yielded three screenshots and one source file link with direct downloading disabled. A sample gallery image returned HTTP 200, `image/png`, 8,212 bytes.

Fourteen deterministic provider tests (existing source tests plus five GameBrew tests) passed locally using Kotlin 2.2.21, JUnit and MockWebServer. This was a JVM-only check; no APK was built locally. Fixtures are attributed under `app/src/test/resources/GAMEBREW-LICENSE.md`.

APK and Android UI build outcomes for this revision are left to the user, who requested ownership of GitHub Actions monitoring. No CI or on-device pass is claimed for this addition.
