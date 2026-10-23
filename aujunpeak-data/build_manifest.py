#!/usr/bin/env python3
"""Build/publish Aujunpeak server-driven UI manifest.

Usage:
  python3 build_manifest.py           # keep current version, rebuild hashes
  python3 build_manifest.py --bump    # increment data version and rebuild hashes
  python3 build_manifest.py --version 12
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "manifest.json"
UI = ROOT / "ui.json"
ASSETS = ROOT / "assets"
MAX_UI = 2 * 1024 * 1024
MAX_ASSET = 20 * 1024 * 1024


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bump", action="store_true", help="increment current data version")
    parser.add_argument("--version", type=int, help="set exact data version")
    args = parser.parse_args()

    if not UI.is_file():
        raise SystemExit("ui.json not found")
    if UI.stat().st_size > MAX_UI:
        raise SystemExit("ui.json exceeds 2 MB limit")

    ui = load_json(UI)
    if int(ui.get("schema", 2)) not in (1, 2):
        raise SystemExit("Unsupported ui.json schema")
    if not isinstance(ui.get("screens", {}), dict):
        raise SystemExit("ui.json: screens must be an object")

    old = {}
    if MANIFEST.exists():
        try:
            old = load_json(MANIFEST)
        except Exception:
            old = {}

    current = int(old.get("version", ui.get("version", 1)))
    version = args.version if args.version is not None else (current + 1 if args.bump else current)
    if version < 1:
        raise SystemExit("version must be >= 1")

    # Keep config version synchronized with manifest version.
    ui["schema"] = 2
    ui["version"] = version
    UI.write_text(json.dumps(ui, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    assets = []
    ASSETS.mkdir(exist_ok=True)
    for path in sorted(ASSETS.iterdir()):
        if not path.is_file() or path.name.startswith(".") or path.name.lower().startswith("readme"):
            continue
        if path.stat().st_size > MAX_ASSET:
            raise SystemExit(f"Asset too large (>20 MB): {path.name}")
        assets.append({
            "name": path.name,
            "url": f"assets/{path.name}",
            "sha256": sha256(path),
        })

    manifest = {
        "schema": 2,
        "version": version,
        "minNativeVersionCode": 13,
        "config": "ui.json",
        "configSha256": sha256(UI),
        "assets": assets,
    }
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"OK: manifest.json -> data v{version}; {len(assets)} asset(s)")
    print("Upload/sync this whole aujunpeak-data directory to /var/www/html/aujunpeak-data")


if __name__ == "__main__":
    main()
