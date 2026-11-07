# Adding a provider

1. Investigate the publisher's documented API/feed, repository license and access policy. Prefer structured data; do not scrape protected pages. Verify a real catalogue, details, artwork and source links. Record the verification date and any access restrictions.
2. Implement `GameProvider` in `Providers.kt` (or a new package). Supply a stable ID, name, description, attribution URL and explicit supported console IDs from `Consoles.all`.
3. Implement `fetchGames(refresh)` returning normalized `Game` values with stable IDs (`provider:slug`). The default interface methods supply search, paging, details, screenshots and downloads. For large remote APIs override search/paging and adapt the repository to persist query pages; the current two sources use complete feeds and local paging.
4. Return only metadata supplied by the source. Preserve missing values as null/empty. Do not substitute invented games, dates, developers or genre labels. `created`/`updated` repository dates are not release dates.
5. Register the provider in `AppGraph.registry`. The console selector, source picker, catalogue, details and attribution screens use the same registry and normalized model. Rebuilding the APK is required to ship a new Kotlin adapter; no catalogue UI changes are required.
6. Add local fixtures and deterministic MockWebServer tests. Ordinary tests must never contact live sites. Verify successful, empty, malformed, unavailable and changed responses.
7. Document request limits and cache duration. Use timeouts, cancellation, HTTPS and caching. Do not advertise the provider as verified before a live smoke check succeeds. `tools/provider-smoke.py` demonstrates optional verification outside ordinary tests.

## Distribution review

A catalogue/repository license does not license every game or bundled asset. `GameFile.authorization` must remain null unless an individual distribution has been reviewed. The UI then exposes only the source website. A new direct-download grant must tie the exact game/source identity, license evidence and release URL path to the reviewed file publisher. Add a negative test preventing a changed source identity from inheriting that grant.

Currently only Universal-DB's `2048---3ds-port` is reviewed. Its publisher explicitly distributes the standalone port under MIT and identifies the original 2048 implementation as MIT. Its upstream LICENSE and release were checked on 2026-11-07. No emulator, commercial game data or BIOS is required in that download. This review is deliberately narrower than the catalogue's list of homebrew titles.

## Existing sources

- Universal-DB: `https://db.universal-team.net/data/full.json`. The upstream README explicitly recommends this feed and requests attribution. Only category `game` is included; DS/3DS filtering uses `systems`. Descriptions are converted from embedded HTML to plain text with Jsoup. No external HTML scraping is used.
- Homebrew Hub GBA: `gbadev-org/games`, documented by `gbdev/homebrewhub/DATABASES.md`. GitHub's recursive tree API discovers entry manifests; public raw JSON and images supply metadata/assets. At most two raw requests run concurrently, with 150 ms pacing and a six-hour persisted cache. Initial sync reads ~251 manifests and can be slow. An individual manifest network failure fails the refresh rather than silently publishing a partial catalogue. JSON entries with invalid metadata are skipped.
- `hh3.gbdev.io` and `hh.gbdev.io` search APIs return 403 in this environment. No protections are bypassed. The API integration, GB/GBC and NES repository support are deferred. Unsupported console categories show an informative empty state.

Sources:
- https://github.com/Universal-Team/db#using-the-data
- https://github.com/gbdev/homebrewhub/blob/main/DATABASES.md
- https://github.com/gbdev/homebrewhub/blob/master/API.md
- https://github.com/gbadev-org/games
- https://github.com/0rientd/2048-port-3ds/blob/main/LICENSE
