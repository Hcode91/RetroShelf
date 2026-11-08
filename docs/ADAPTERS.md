# Adding a provider

1. Investigate the publisher's documented API/feed, repository license and access policy. Prefer structured data; do not scrape protected pages. Verify a real catalogue, details, artwork and source links. Record the verification date and any access restrictions.
2. Implement `GameProvider` in `Providers.kt` (or a new package). Supply a stable ID, name, description, attribution URL and explicit supported console IDs from `Consoles.all`.
3. Implement `fetchGames(refresh)` returning normalized `Game` values with stable IDs (`provider:slug`). The default interface methods supply search, paging, details, screenshots and downloads. For large remote APIs override search/paging and adapt the repository to persist query pages; complete feeds use local paging, while `PagedGameProvider` persists explicit remote page cursors.
4. Return only metadata supplied by the source. Preserve missing values as null/empty. Do not substitute invented games, dates, developers or genre labels. `created`/`updated` repository dates are not release dates.
5. Register the provider in `SourceRegistry.create(http)`. The console selector, source picker, catalogue, details and attribution screens use the same registry and normalized model. Rebuilding the APK is required to ship a new Kotlin adapter; no catalogue UI changes are required.
6. Add local fixtures and deterministic MockWebServer tests. Ordinary tests must never contact live sites. Verify successful, empty, malformed, unavailable and changed responses.
7. Document request limits and cache duration. Use timeouts, cancellation, HTTPS and caching. Do not advertise the provider as verified before a live smoke check succeeds. `tools/provider-smoke.py` demonstrates optional verification outside ordinary tests.

## Distribution review

A catalogue/repository license does not license every game or bundled asset. `GameFile.authorization` must remain null unless an individual distribution has been reviewed. The UI then exposes only the source website. A new direct-download grant must tie the exact game/source identity, license evidence and release URL path to the reviewed file publisher. Add a negative test preventing a changed source identity from inheriting that grant.

Currently only Universal-DB's `2048---3ds-port` is reviewed. Its publisher explicitly distributes the standalone port under MIT and identifies the original 2048 implementation as MIT. Its upstream LICENSE and release were checked on 2026-11-07. No emulator, commercial game data or BIOS is required in that download. This review is deliberately narrower than the catalogue's list of homebrew titles.

## Existing sources

- Universal-DB: `https://db.universal-team.net/data/full.json`. The upstream README explicitly recommends this feed and requests attribution. Only category `game` is included; DS/3DS filtering uses `systems`. Descriptions are converted from embedded HTML to plain text with Jsoup. No external HTML scraping is used.
- Homebrew Hub GBA: `gbadev-org/games`, documented by `gbdev/homebrewhub/DATABASES.md`. GitHub's recursive tree API discovers entry manifests; public raw JSON and images supply metadata/assets. At most two raw requests run concurrently, with 150 ms pacing and a persisted catalogue that is refreshed only on user request. Initial sync reads ~251 manifests and can be slow. An individual manifest network failure fails the refresh rather than silently publishing a partial catalogue. JSON entries with invalid metadata are skipped.
- `hh3.gbdev.io` and `hh.gbdev.io` search APIs return 403 in this environment. No protections are bypassed. Homebrew Hub's direct API and its GB/GBC/NES repository adapters remain deferred; GameBrew provides GB/GBC separately. Unsupported console categories show an informative empty state.

Sources:
- https://github.com/Universal-Team/db#using-the-data
- https://github.com/gbdev/homebrewhub/blob/main/DATABASES.md
- https://github.com/gbdev/homebrewhub/blob/master/API.md
- https://github.com/gbadev-org/games
- https://github.com/0rientd/2048-port-3ds/blob/main/LICENSE

## GameBrew homebrew

`GameBrew` is configured per console and appears under each console's dedicated **GameBrew homebrew** section. The existing GBA ID remains `gamebrew`, preserving saved entries and favorites. Additional provider IDs are `gamebrew-switch`, `gamebrew-wiiu`, `gamebrew-wii` and `gamebrew-3ds`. Search, filters, favorites, recent history and provider management use the existing shared UI.

Catalogue: the public MediaWiki API at `https://www.gamebrew.org/api.php?action=parse&page=List_of_GBA_homebrew_games&prop=text&format=json&formatversion=2&maxlag=5`. Jsoup combines rendered catalogue tables (title, description, author, genre, updated date) with gallery images, deduplicating by article URL and preserving multiple genre memberships. The Gallery View URL fragment selects a website tab; it is not a separate catalogue endpoint.

Details: the same API parses an individual article only when the user opens it. Infobox metadata, introductory description, screenshot section and download-link metadata enrich the cached summary. Detail refresh failures retain saved information and show a message. Detail timestamps are local cache metadata and are not displayed as release dates. Updated dates are never labelled as original release dates.

Access checked 2026-11-07: `robots.txt` permits general access and search/reference reuse, while disallowing named training crawlers and certain wiki namespaces. This adapter does not access those namespaces. The website footer licenses wiki content under CC BY-SA 4.0 unless otherwise noted. In-app attribution links the source article and metadata license and identifies plain-text conversion. Artwork and game binaries retain their own terms. Arbitrary Cargo queries returned `permissiondenied`; the adapter uses the accessible public parse API, with no authentication/challenge bypass.

All GameBrew console adapters share a single request limiter: at most one API request at a time, separated by at least one second. Each adapter loads only its own catalogue, has an independent persistent cache and can be enabled/disabled independently. `maxlag=5` respects MediaWiki load; API errors and HTTP restrictions fail normally. Catalogues and enriched details are saved without time expiry. Pull-to-refresh replaces the catalogue; Refresh game details updates an article. The repository passes the saved entry to the detail adapter, so first-time detail loading after restart does not request the catalogue again. The adapter never crawls all detail pages during catalogue loading. No GameBrew binary currently has a direct-download authorization grant; users open its original article for downloads.

Local fixture attribution: `app/src/test/resources/GAMEBREW-LICENSE.md`. The parser/provider tests exercise real response excerpts, duplicate entries, unsafe article links, missing layouts, empty results, metadata normalization, API errors, request encoding, catalogue caching and on-demand details.


### Additional verified GameBrew catalogues

| Console | API page parameter | Provider ID | Verified games (2026-11-07) |
| --- | --- | --- | ---: |
| Switch | `List_of_Switch_homebrew_games` | `gamebrew-switch` | 405 |
| Wii U | `List_of_Wii_U_homebrew_games` | `gamebrew-wiiu` | 35 |
| Wii | `List_of_Wii_homebrew_games` | `gamebrew-wii` | 360 |
| 3DS | `List_of_3DS_homebrew_games` | `gamebrew-3ds` | 450 |

Switch and Wii U are registered console categories. Existing Wii and 3DS categories are reused; Universal-DB remains available alongside GameBrew for 3DS. Platform labels and provider IDs are passed into the shared parser so cached games, searches and favorites remain scoped correctly. Legitimate colons inside game titles are supported while MediaWiki administrative/file/template namespaces are excluded.

The same public parse API and CC BY-SA attribution policy apply. One detail page per console and sample artwork were checked live. Missing screenshots are left empty. No new GameBrew binary has a direct-download grant. Catalogue tab fragments select website presentation only; they do not require extra API calls or a WebView.

## Ziperto Switch listings

Provider ID `ziperto` appears under **Switch → Switch catalogues**. Its public WordPress API and category 3534 were verified on 2026-11-07. The category reported 13,812 listings. `robots.txt` does not disallow the public category, media uploads or `/wp-json/` endpoints; restricted admin/plugin/login paths are not accessed. The AMP URL is a presentation variant; the adapter uses canonical HTTPS article URLs.

The adapter requests `/wp-json/wp/v2/posts?categories=3534&per_page=100&page=1&orderby=date&order=desc&_fields=id,link,title,excerpt,content,modified_gmt`. It reads `X-WP-Total` and `X-WP-TotalPages`, loading only one page per explicit request. All source requests are serialized and paced at least one second apart. No whole-site crawl runs.

`PagedGameProvider.fetchCataloguePage()` returns a `CatalogueBatch`; the repository saves normalized entries plus the next-page cursor and source listing count in Room. **Load older listings from website** explicitly requests another 100 records. Local search/sort/filter apply to saved records, which the UI labels accordingly. Reopening the source reuses saved entries and cursor without a request. Pull-to-refresh replaces the paged snapshot with the newest page; favorites/history survive. A version-2 → version-3 Room migration adds nullable pagination fields and preserves existing snapshots.

The source provides article content within each listing page, so cover artwork, description, screenshots, original release/developer/publisher/genre metadata are normalized together and saved as opened-detail data. Opening one of those saved games does not request a second article. **Refresh game details** explicitly requests `/posts/{numeric-id}`. WordPress publication timestamps are not used as game release dates.

Download links on the sample page lead to intermediary hosts. The adapter does not resolve them, follow protected download flows, or enqueue binaries. The user opens the original article in their browser for those links. No metadata license or publisher authorization is inferred from website availability. It is labelled as a Switch catalogue rather than homebrew.

To add another paged adapter, implement `PagedGameProvider`, return page cursors/counts, register it, and add page-response fixtures plus repository tests for cache reuse, duplicate IDs, failed-page retry and cursor persistence. Avoid downloading every remote page at startup.


## RPGMaker, further homebrew and website catalogues

Source registration now lives in `SourceRegistry.create(http)`. PC is labelled **PC games** and groups `rpgmaker` and `rpgmaker-archive` under **RPGMaker**. PS Vita, PS3 and PS4 are registered console categories.

| Source | Native access | Page size / request behavior |
| --- | --- | --- |
| RPGMaker.net | Public `/games/?portal=yes&p=N` HTML tables and public game description pages | 15 entries observed on page 1; next links drive explicit paging |
| Internet Archive RPGMaker.net archive | Public `advancedsearch.php` collection query and `/metadata/{identifier}` | 50 records per requested page; 3,820 records verified |
| NXbrew | Public WordPress category 22 at `nxbrew.me/wp-json/wp/v2/posts` | 100 per explicit page; 1,308 category records verified |
| SuperPSX PS4 | Public category HTML and article HTML | 20 entries observed on page 1; explicit next-page links |
| SteamRIP PC | Original website WebView | `robots.txt` disallows general crawlers; no native index or game entries fabricated |
| NoPayStation PS3 | Original website WebView | `robots.txt` disallows general crawlers; no native index, package/licensing-key extraction or fabricated game data |

RPGMaker.net and Archive robots policies permit these public catalogue/metadata paths. NXbrew permits its public category and REST paths. SuperPSX allows catalogue/article HTML but explicitly disallows `/wp-json/` and site search paths, so the adapter uses Jsoup over the permitted HTML. All native requests are paced and only user-selected sources/pages are fetched. Website-only entries have `nativeBrowsing=false`, an explicit status, and are excluded from native Search. SteamRIP and NoPayStation now use the embedded original-website view; future unsupported website entries fall back to the external browser.

NXbrew uses the same parameterized `WordPressSource` implementation as Ziperto. It parses factual two-column information tables as well as colon-separated metadata. Source labels are retained: its first 100 records included 89 Switch and 11 Switch 2 records. The adapter saves only games matching its supported Switch console, while the parser retains the true platform label. It never changes a Switch 2 label to Switch.

GameBrew now registers `gamebrew-ds`, `gamebrew-gb`, `gamebrew-vita` and `gamebrew-psp`. Verified catalogue sizes were 1,448 DS, 782 Game Boy-family, 1,059 Vita and 1,892 PSP entries. The GB table has an extra System column; headers determine author/update positions. DMG and CGB values drive GB/GBC filtering, with the same source/cache shared by both console selectors. PSP had one entry without artwork; it uses the normal image placeholder.

Original websites handle new-source binary downloads. The Archive collection is a real collection with game child items, not one fabricated game representing the collection. No upload/publication timestamp is presented as an original game release date. No source game license is inferred from its host's availability.

Fixture provenance is recorded in `app/src/test/resources/ADDITIONAL-SOURCE-FIXTURES.md` and `GAMEBREW-LICENSE.md`.


## Display-only original website mode

`WebsiteScreen` handles only `steamrip` and `nopaystation-ps3`, with origin checks in `WebsitePolicy`. It loads the original website once per navigation/restoration and uses `website-reader.js` to change current-page CSS/classes. The script exports no game names, pictures, package/license data or native catalogue records. There is no native JavaScript interface, off-page crawler or automatic detail-page fetching.

The reader recognizes SteamRIP A–Z entries / supported homepage cards and NoPayStation result-table rows. It preserves existing name links/images, hides unrelated current-page elements and restores the original presentation when disabled. Dynamic row insertions are handled by a debounced local DOM observer. Recognized challenge/login/consent gates are checked in the original layout and remain visible. Unsupported layouts and blocked inline styles retain the original page. No selector match is treated as a reason to bypass protections.

The supplied index pages have no game cover artwork in their loaded HTML; filtering cannot create those images. A SteamRIP homepage navigation button is explicit and user initiated. This website mode uses normal WebView caching, which can revalidate/request resources; it is intentionally separate from permanent Room metadata caching. Display toggles do not reload pages. File/content access, cleartext mixed content, automatic popups and third-party cookies are disabled; HTTPS/TLS defaults and Safe Browsing remain enabled. External links require a user gesture and open the external browser. Downloads are handed back to the user's browser through the Browser control.

Local Playwright fixture tests live in `tools/reader-tests` and run in the Android build workflow. A separate Android WebView fixture test verifies script activation/restoration in the device-test workflow. Neither ordinary test contacts live websites.
