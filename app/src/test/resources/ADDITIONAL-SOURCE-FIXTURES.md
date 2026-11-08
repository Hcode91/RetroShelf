# Additional source fixtures

Public responses checked 2026-11-07:

- `rpgmaker-catalogue.html`: two actual rows and the next-page link from https://rpgmaker.net/games/?portal=yes. `rpgmaker-details.html` retains the cover metadata and a short description excerpt from https://rpgmaker.net/games/13454/.
- `superpsx-catalogue.html`: two actual article cards and pagination from https://www.superpsx.com/category/ps4/ps4-games-free/. `superpsx-details.html` retains cover metadata and a short overview excerpt from its first article. The native adapter uses allowed HTML pages, not the site's disallowed WordPress API.
- `archive-search.json`: two public search API records from collection `rpgmaker-net-game-archive`. `archive-detail.json` retains the metadata object of `rmn-game-1432-eternity-s-point`.
- `nxbrew-posts.json`: two public WordPress records in category 22, restricted to metadata, source information tables, a short paragraph and image URLs. Both explicitly identify Switch 2 in source metadata and test exclusion from Switch-only browsing.

HTML fixtures omit unrelated ads, scripts and navigation. Metadata/linked artwork belong to the respective sites and creators; no open license is asserted for these fixtures. No binaries, license keys or intermediary-host responses are bundled. SteamRIP and NoPayStation have no scraped game fixtures because their general crawler policies disallow indexing; only browser source entries are registered.
