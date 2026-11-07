# RetroShelf • Android proof of concept

RetroShelf is an **experimental multi-console game-catalogue UI** that shows game artwork, descriptions, and original download links without showing whole websites. It does not ship games or copyrighted ROMs. All sample games in preview mode are **fictional** and cannot be downloaded.

## Try it on any phone or desktop without building an APK

Open `app/src/main/assets/index.html` directly in a browser (or use the separate `RetroShelf_POC.html` file). Select a console. The page tries to load live homebrew metadata.

### Supported live adapters

- Game Boy, Game Boy Color, Game Boy Advance: [Homebrew Hub](https://github.com/gbdev/homebrewhub/blob/main/API.md) search API (`/api/search`).
- Nintendo DS, Nintendo 3DS: [Universal-DB](https://db.universal-team.net/) public `full.json` feed.

Live catalogue results require internet access and cross-origin access permitted by the source website. Homebrew Hub's live endpoint wasn't reachable from the development environment, so its browser integration has not been verified. For any network/CORS/API failure, clearly labelled fictional demo entries appear and downloads are **disabled**. In a production app, replace fragile website scraping with authorised APIs or a controlled backend adapter, with permission from sites and publishers where appropriate.

## Build an Android APK using GitHub Actions (no local build required)

1. Create a new GitHub repository.
2. Upload the **contents** of this folder into the repo root (including the hidden `.github` directory).
3. In the repository's **Actions** tab, choose **Build RetroShelf Android APK** → **Run workflow** (or push a commit).
4. Open the completed workflow run and download the `RetroShelf-debug-apk` artifact.
5. Unzip the artifact, then install `app-debug.apk` on your phone after reviewing Android's install warnings.

The APK is only a debug proof of concept, not a signed Play Store release. The build workflow has **not** been run here because the build environment cannot reach Google's dependencies. Target: Android 10+ (min SDK 29).

## Implementation

- Android WebView loads the HTML locally through a controlled HTTPS-like origin using WebViewClient interception.
- `fetch()` loads public metadata from selected third-party sources.
- Each source has its own normalisation adapter and maps records into common `{title, author, description, image, downloads}` objects.
- When a user taps a downloadable file, Android's `DownloadManager` saves the original source URL into Downloads; the app does not host files or bypass access controls.
- Visit-source links open in a browser.
- Search, A–Z sorting, author sorting, detail screens, and a responsive layout are included.

## Limitations / next steps

- Source sites may change their feed schemas, restrict CORS, rate-limit requests, or prohibit automated retrieval. Treat all integrations as provisional until individually tested and authorised.
- Homebrew Hub pagination and JSON field naming were implemented from its public documentation, but have not been end-to-end verified.
- The HTML preview isn't proof the native APK is buildable until GitHub Actions succeeds.
- Before public release, implement domain allowlisting, content-safety filtering, appropriate trademarks/attribution, a proper repository permission audit, checksums where available, download progress, storage import, and a privacy policy.
- No scraping/login bypasses, paywall evasion, or commercial ROM discovery are implemented.

This proof of concept deliberately links only to homebrew data sources; it does not provide piracy-oriented catalogues.
