# GameBrew response fixtures

`gamebrew-catalogue.html` is a reduced excerpt from the public MediaWiki parse response for [List of GBA homebrew games](https://www.gamebrew.org/wiki/List_of_GBA_homebrew_games), retrieved 2026-11-07. It retains two Action rows, two Puzzle rows and two gallery cards, with surrounding markup simplified for deterministic tests.

`gamebrew-details.json` is the public parse response for [Minicraft GBA](https://www.gamebrew.org/wiki/Minicraft_GBA), retrieved 2026-11-07.

Text/metadata copyright GameBrew contributors; licensed under [Creative Commons Attribution-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/), as stated in GameBrew's page footer. These fixtures and their adaptations remain under that license. Refer to the linked pages and their revision histories for contributor attribution. Image and binary URLs are references only; no artwork or game binaries are bundled in these fixtures.


Additional fixtures retrieved 2026-11-07 (same GameBrew-contributors / CC BY-SA 4.0 attribution and license):

- `gamebrew-switch-catalogue.html`: excerpt from [Switch games](https://www.gamebrew.org/wiki/List_of_Switch_homebrew_games); `gamebrew-switch-details.html`: rendered [Blockamok Remix Switch](https://www.gamebrew.org/wiki/Blockamok_Remix_Switch) article.
- `gamebrew-wiiu-catalogue.html`: excerpt from [Wii U games](https://www.gamebrew.org/wiki/List_of_Wii_U_homebrew_games); `gamebrew-wiiu-details.html`: rendered [Blockamok Remix Wii U](https://www.gamebrew.org/wiki/Blockamok_Remix_Wii_U) article.
- `gamebrew-wii-catalogue.html`: excerpt from [Wii games](https://www.gamebrew.org/wiki/List_of_Wii_homebrew_games); `gamebrew-wii-details.html`: rendered [Blockamok Remix Wii](https://www.gamebrew.org/wiki/Blockamok_Remix_Wii) article.
- `gamebrew-3ds-catalogue.html`: excerpt from [3DS games](https://www.gamebrew.org/wiki/List_of_3DS_homebrew_games); `gamebrew-3ds-details.html`: rendered [Blockamok Remix 3DS](https://www.gamebrew.org/wiki/Blockamok_Remix_3DS) article.

Catalogue excerpts retain the first two Action records plus the Blockamok Remix record/gallery entry where needed, with simplified surrounding markup. Detail HTML comes from the public parse API. Wiki descriptions are preserved even where article text differs between platforms; no inferred corrections or substitute screenshots are added.

Additional 2026-11-07 fixtures (same attribution and CC BY-SA 4.0 license):

- `gamebrew-ds-new-*`: [DS catalogue](https://www.gamebrew.org/wiki/List_of_DS_homebrew_games) and [Blimp Chicken DS](https://www.gamebrew.org/wiki/Blimp_Chicken_DS).
- `gamebrew-gb-new-*`: [Game Boy catalogue](https://www.gamebrew.org/wiki/List_of_GB_homebrew_games) and [WanWan GB](https://www.gamebrew.org/wiki/WanWan_GB).
- `gamebrew-vita-new-*`: [Vita catalogue](https://www.gamebrew.org/wiki/List_of_Vita_homebrew_games) and [The Dark Queen of Mortholme Vita](https://www.gamebrew.org/wiki/The_Dark_Queen_Of_Mortholme_Vita).
- `gamebrew-psp-new-*`: [PSP catalogue](https://www.gamebrew.org/wiki/List_of_PSP_homebrew_games) and [Minicraft PSP by Koutsie](https://www.gamebrew.org/wiki/Minicraft_PSP_by_Koutsie).
- `gamebrew-gbc-system.html` retains one real CGB-only record and its original table headers from the Game Boy catalogue, testing hardware filtering.

Catalogue fixtures retain original table headers, selected rows and their matching gallery cards. Details contain rendered public API HTML. No binaries are bundled.
