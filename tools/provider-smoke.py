#!/usr/bin/env python3
"""Optional live-source verification; never part of deterministic unit tests."""
import concurrent.futures
import json
import time
import urllib.request


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": "RetroShelf-source-verification/0.2"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def main():
    universal = json.loads(get("https://db.universal-team.net/data/full.json"))
    games = [g for g in universal if "game" in g.get("categories", []) and set(g.get("systems", [])) & {"DS", "3DS"}]
    assert games, "No live Universal-DB games"
    approved = next(g for g in games if g.get("slug") == "2048---3ds-port")
    assert approved["license"] == "mit"
    assert b"Permission is hereby granted" in get("https://raw.githubusercontent.com/0rientd/2048-port-3ds/main/LICENSE")
    file = next(iter(approved["downloads"].values()))
    assert file["url"].startswith("https://github.com/0rientd/2048-port-3ds/releases/download/")
    payload = get(file["url"])
    assert len(payload) == file["size"], "Approved release file size changed"
    assert get(approved["image"]), "Approved game artwork unavailable"
    repo = "gbadev-org/games"
    tree = json.loads(get(f"https://api.github.com/repos/{repo}/git/trees/master?recursive=1"))
    assert not tree.get("truncated")
    paths = [entry["path"] for entry in tree["tree"] if entry["path"].startswith("entries/") and entry["path"].endswith("/game.json")]
    def entry(path):
        time.sleep(0.15)
        return json.loads(get(f"https://raw.githubusercontent.com/{repo}/master/{path}"))
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        manifests = list(pool.map(entry, paths))
    gba = [g for g in manifests if g.get("typetag") == "game" and g.get("platform") == "GBA" and g.get("title") and g.get("slug")]
    assert gba, "No GBA games"
    sample = next(g for g in gba if g.get("screenshots"))
    assert get(f"https://raw.githubusercontent.com/{repo}/master/entries/{sample['slug']}/{sample['screenshots'][0]}")
    print(json.dumps({"universal_games": len(games), "gba_manifests": len(manifests), "gba_games": len(gba), "approved_download_bytes": len(payload), "artwork": "verified for both sources"}, indent=2))


if __name__ == "__main__":
    main()
