# Adding a provider

1. Investigate the publisher's documented API/feed, repository license and access policy. Prefer structured data; do not scrape protected pages. Verify a real catalogue, details, artwork and source links. Record the verification date and any access restrictions.
2. Implement `GameProvider` in `Providers.kt` (or a new package). Supply a stable ID, name, description, attribution URL and explicit supported console IDs from `Consoles.all`.
3. Implement `fetchGames(refresh)` returning normalized `Game` values with stable IDs (`provider:slug`). The default interface methods supply search, paging, details, screenshots and downloads. For large remote APIs override search/paging and adapt the repository to persist query pages; the current sources use complete feeds and local paging.
4. Return only metadata supplied by the source. Preserve missing values as null/empty. Do not substitute invented games, dates, developers or genre labels. `created`/`updated` repository dates are not release dates.
5. Register the provider in `AppGraph.registry`. The console selector, source picker, catalogue, details and attribution screens use the same registry and normalized model. Rebuilding the APK is required to ship a new Kotlin adapter; no catalogue UI changes are required.
6. Add local fixtures and deterministic MockWebServer tests. Ordinary tests must never contact live sites. Verify successful, empty, malformed, unavailable and changed responses.
7. Document request limits and cache duration. Use timeouts, cancellation, HTTPS and caching. Do not advertise the provider as verified before a live smoke check succeeds. `tools/provider-smoke.py` demonstrates optional verification outside ordinary tests.

## Distribution review

A catalogue/repository license does not license every game or bundled asset. `GameFile.authorization` must remain null unless an individual distribution has been reviewed. The UI then exposes only the source website. A new direct-download grant must tie the exact game/source identity, license evidence and release URL path to the reviewed file publisher. Add a negative test preventing a changed source identity from inheriting that grant.

Currently only Universal-DB's `2048---3ds-port` is reviewed. Its publisher explicitly distributes the standalone port under MIT and identifies the original 2048 implementation as MIT. Its upstream LICENSE and release were checked on 2026-11-07. No emulator, commercial game data or BIOS is required in that download. This review is deliberately narrower than the catalogue's list of homebrew titles.

## Existing sources

- Universal-DB: `https://db.universal-team.net/data/full.json`. The upstream README explicitly recommends this feed and requests attribution. Only category `game` is included; DS/3DS filtering uses `systems`. Descriptions are converted from embedded HTML to plain text with Jsoup. No external HTML scraping is used.
- Homebrew Hub GBA: `gbadev-org/games`, documented by `gbdev/homebrewhub/DATABASES.md`. GitHub's recursive tree API discovers entry manifests; public raw JSON and images supply metadata/assets. At most two raw requests run concurrently, with 150 ms pacing and a persisted catalogue that is refreshed only on user request. Initial sync reads ~251 manifests and can be slow. An individual manifest network failure fails the refresh rather than silently publishing a partial catalogue. JSON entries with invalid metadata are skipped.
- `hh3.gbdev.io` and `hh.gbdev.io` search APIs return 403 in this environment. No protections are bypassed. The API integration, GB/GBC and NES repository support are deferred. Unsupported console categories show an informative empty state.

Sources:
- https://github.com/Universal-Team/db#using-the-data
- https://github.com/gbdev/homebrewhub/blob/main/DATABASES.md
- https://github.com/gbdev/homebrewhub/blob/master/API.md
- https://github.com/gbadev-org/games
- https://github.com/0rientd/2048-port-3ds/blob/main/LICENSE

## GameBrew GBA homebrew

`GameBrew` is registered independently as provider `gamebrew`, under the dedicated **GameBrew homebrew** section of the GBA source screen. Search, filters, favorites, recent history and provider management use the existing shared UI.

Catalogue: the public MediaWiki API at `https://www.gamebrew.org/api.php?action=parse&page=List_of_GBA_homebrew_games&prop=text&format=json&formatversion=2&maxlag=5`. Jsoup combines rendered catalogue tables (title, description, author, genre, updated date) with gallery images, deduplicating by article URL and preserving multiple genre memberships. The Gallery View URL fragment selects a website tab; it is not a separate catalogue endpoint.

Details: the same API parses an individual article only when the user opens it. Infobox metadata, introductory description, screenshot section and download-link metadata enrich the cached summary. Detail refresh failures retain saved information and show a message. Detail timestamps are local cache metadata and are not displayed as release dates. Updated dates are never labelled as original release dates.

Access checked 2026-11-07: `robots.txt` permits general access and search/reference reuse, while disallowing named training crawlers and certain wiki namespaces. This adapter does not access those namespaces. The website footer licenses wiki content under CC BY-SA 4.0 unless otherwise noted. In-app attribution links the source article and metadata license and identifies plain-text conversion. Artwork and game binaries retain their own terms. Arbitrary Cargo queries returned `permissiondenied`; the adapter uses the accessible public parse API, with no authentication/challenge bypass.

At most one GameBrew API request at a time, separated by at least one second. `maxlag=5` respects MediaWiki load; API errors and HTTP restrictions fail normally. Catalogues and enriched details are saved without time expiry. Pull-to-refresh replaces the catalogue; Refresh game details updates an article. The repository passes the saved entry to the detail adapter, so first-time detail loading after restart does not request the catalogue again. The adapter never crawls all detail pages during catalogue loading. No GameBrew binary currently has a direct-download authorization grant; users open its original article for downloads.

Local fixture attribution: `app/src/test/resources/GAMEBREW-LICENSE.md`. The parser/provider tests exercise real response excerpts, duplicate entries, unsafe article links, missing layouts, empty results, metadata normalization, API errors, request encoding, catalogue caching and on-demand details.
