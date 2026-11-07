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

## Persistent saved-entry policy — 2026-11-07

Removed timed expiry from catalogue and opened-detail storage. Added persistent snapshot markers for empty catalogues, a version-1 → version-2 Room migration preserving existing data, an explicit detail-refresh button, and a saved-entry GameBrew detail path that does not reload the catalogue after restart.

Twenty-one deterministic JVM provider/repository tests passed locally. Cache tests use the production repository with an in-memory DAO implementation; the local harness strips Room annotations from the DAO contract to compile without Android tooling. Tests cover old entries without network calls, empty-result persistence, explicit refresh/clear behavior, and article-only detail requests. Migration SQL was also executed against SQLite and preserved snapshot, favorite and history values. `CacheMigrationTest` adds actual Room upgrade/reopen coverage to the Android emulator workflow; that instrumentation result has not been inspected. No APK was built locally and no CI outcomes were monitored.

## GameBrew platform expansion — 2026-11-07

The production Kotlin parser processed the four public MediaWiki catalogue responses:

| Console | Games | Genres | Entries with artwork URLs | Sample detail screenshots |
| --- | ---: | ---: | ---: | ---: |
| Switch | 405 | 15 | 405 | 2 |
| Wii U | 35 | 10 | 35 | 0 |
| Wii | 360 | 15 | 360 | 2 |
| 3DS | 450 | 16 | 450 | 2 |

Sample details were the respective Blockamok Remix articles. Wii U's article has no screenshot section, which is preserved as missing metadata. Sample artwork returned HTTP 200 / image/png: 29,005 bytes for the shared Switch/Wii U image, 29,023 bytes for Wii, and 17,721 bytes for 3DS. Shared artwork URLs were checked only once.

Thirty-eight deterministic JVM provider/repository tests passed locally, including parameterized tests for each new platform's real catalogue/detail fixtures, selected-platform requests, scoped identifiers, cached catalogues, and article-only detail loading after restart. Tests also cover legitimate colon-containing game titles while excluding administrative wiki namespaces. No APK was built locally. Android build and emulator outcomes for this revision remain uninspected, as requested.

## Ziperto Switch source — 2026-11-07

Verified the public category, robots.txt and WordPress API. Category ID 3534 reported 13,812 listings. Two small live listing pages normalized into four Switch records with four cover URLs, eight screenshot URLs and source release/developer/publisher/genre metadata. A sample cover returned HTTP 200, image/webp, 14,944 bytes. No intermediary download host was contacted.

The adapter defaults to 100 records per explicitly requested page. It saves source entries and the next-page cursor in Room; opening already supplied details requires no additional article request. The UI labels search/filter scope as saved listings, provides an explicit older-page button, and opens original articles for download links.

Forty-five deterministic JVM provider/repository tests passed locally, including pagination, cursor persistence across repository reopening, deduplication without losing favorites, failed-page retry, empty responses and API/schema errors. Version-1 → version-2 → version-3 SQL migration checks preserved existing snapshots and added nullable page/count fields. The Android migration instrumentation test also checks cursor/count persistence after reopening; its CI result has not been inspected. No APK was built locally, and build monitoring remains with the user.

## RPGMaker, further homebrew and source expansion — 2026-11-07

Public live responses were verified and processed by the production Kotlin parsers:

| Source | Verified data |
| --- | --- |
| RPGMaker.net | 15 actual first-page game records and a next-page link; public Dragon Child details |
| Internet Archive RPGMaker.net collection | 3,820 child records via public collection search; individual archived-game metadata |
| NXbrew | Public WordPress category 22, 1,308 total source records; first 100 included 89 Switch and 11 explicit Switch 2 records |
| SuperPSX PS4 | 20 actual first-page cards, next-page link and public article details via HTML |
| GameBrew DS | 1,448 records with artwork URLs |
| GameBrew Game Boy family | 782 records with artwork URLs; source System column distinguishes DMG/CGB |
| GameBrew Vita | 1,059 records with artwork URLs |
| GameBrew PSP | 1,892 records, 1,891 with artwork URLs; missing art remains missing |

SteamRIP and NoPayStation returned public website pages, but their robots policies disallow general crawling. They are registered as browser links with explicit status and excluded from native Search. SuperPSX's robots policy disallows its WordPress API, so runtime integration uses allowed category/article HTML only. No NoPayStation package or license-key extraction is implemented.

Representative artwork returned HTTP 200 for RPGMaker.net, Archive, SuperPSX and each added GameBrew platform. All loaded game records/pages retain manual-refresh-only caching; only explicitly selected sources/pages make metadata requests.

Sixty-one deterministic JVM tests passed, including new source fixtures, RPGMaker grouping, browser-only fallback behavior, new console selection, HTML layout failures, archive paging, NXbrew platform labels, Game Boy header-based author/date parsing, and Color-only game filtering. Fixture provenance is documented alongside the fixtures. Targeted source tests were repeated after trimming unrelated HTML/prose from fixtures. No APK was built locally; Android builds/emulator outcomes are left uninspected for the user to monitor.
