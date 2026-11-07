# RetroShelf

Native Android catalogue for community homebrew, built with Kotlin, Jetpack Compose / Material 3, MVVM, Coroutines / Flow, OkHttp, Gson, Jsoup, Coil and Room. No WebView is used. Original HTML/Java proof-of-concept files are preserved under `reference/` for design provenance and are not packaged in the APK.

## Install without Android Studio

1. Open [Build RetroShelf Android APK](https://github.com/Hcode91/RetroShelf/actions/workflows/android-debug.yml).
2. Choose a successful run on `feature/native-android` (or `main` after merging the PR).
3. Download **RetroShelf-debug-apk** under Artifacts, unzip, and open `app-debug.apk` on an Android 10+ device. Android may ask you to allow installation from the app opening the APK.

All APK builds run through GitHub Actions. The workflow installs JDK 17, Gradle 8.13 and Android SDK 36, executes unit tests and Android lint, builds the debug APK, and uploads verification reports plus the APK. `workflow_dispatch` becomes available in the Actions UI after the workflow is on the default branch; pushes and PRs build the feature branch immediately. Artifacts expire after 30 days; rerun the workflow to recreate them. No signing secrets are needed for debug builds.

## What works

- Nineteen console categories grouped by manufacturer. Console → verified source → catalogue → game details navigation.
- Universal-DB DS/3DS games and Homebrew Hub's official GBA repository, using actual metadata and artwork. No fictional fallback entries.
- Per-source title/description/developer search, source genre/tag filtering, title/update/developer sorting, grid/list view, local paging, pull-to-refresh, error/retry states and offline cached results.
- Details show available source description, screenshots, author, release metadata, license, file formats/sizes, and publisher links. Missing metadata is labelled rather than invented.
- Persistent favorites and recently viewed games in Room. Six-hour catalogue cache, image cache, provider toggles, dark/light theme and source attribution.
- Android DownloadManager progress, completion notifications, cancel, failure history and retry. Public Downloads/RetroShelf or app storage; a completed download can be copied to a user-selected Storage Access Framework destination. Files are never installed or executed.
- Direct downloads are limited to individually reviewed distributions. The MIT-licensed 2048 3DS port is initially enabled; unreviewed catalogue files display the source website instead.

## Source verification and limitations

See [adapter documentation](docs/ADAPTERS.md) for public endpoints, verification evidence and request limits. Universal-DB and the GBA upstream repository were reached on 2026-11-07. Homebrew Hub's direct API returned 403, so GBA uses the documented public repository. GB/GBC and other source integrations are not advertised as available. A first GBA sync may take a minute or more; later visits use Room cache. GitHub API rate-limit errors appear as source errors, with cached results available if previously loaded.

Universal search is deferred: the Search tab chooses one enabled source/platform at a time. This avoids unnecessary requests to providers the user is not browsing. Direct download authorization is intentionally limited; homebrew labelling alone is insufficient evidence of asset rights. The app does not scrape protected pages, bypass DRM/payment/authentication, download commercial ROM catalogues, or automatically import files into emulators.

## Verification

`gradle :app:testDebugUnitTest :app:lintDebug` is executed in Actions. Tests use checked-in real-response fixtures and MockWebServer, covering console/provider filtering, normalization, search/sort/paging/empty results, malformed source data, HTML description conversion, caching/favorites/recent behavior, HTTP failures and unsafe download URLs. `tools/provider-smoke.py` is an optional manual live check, excluded from unit tests.

On-device checks: rotate Home and a catalogue; search “2048” on 3DS; open details, favorite it and relaunch; download its reviewed `.3dsx`, observe Downloads/notifications, save a copy to a chosen document destination; disable network and refresh a cached catalogue; verify that a fresh uncached provider shows a retry error. No emulator/hardware execution result should be inferred from unit tests.

## Add a website

Implement `GameProvider`, normalize data into `Game`, register it in `AppGraph`, add response fixtures and pass verification before enabling it. The catalogue UI requires no changes. Full instructions: [docs/ADAPTERS.md](docs/ADAPTERS.md).

## Signed releases later

Create an upload keystore outside the repository. Store its base64 bytes, store password, alias and key password as GitHub Actions encrypted secrets. In a separate release workflow, decode the keystore into the runner's temporary directory and pass secret values through environment variables into an Android release signing configuration. Run tests/lint, build `assembleRelease`/`bundleRelease`, and upload signed artifacts. Never commit keystores, passwords or populated `local.properties`. Back up the release key securely; it is required to update installed release builds. Debug and release signatures are different.
