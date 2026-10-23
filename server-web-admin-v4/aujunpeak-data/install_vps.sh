#!/usr/bin/env bash
set -euo pipefail
DEST="/var/www/html/aujunpeak-data"
SRC="$(cd "$(dirname "$0")" && pwd)"
sudo mkdir -p "$DEST"
sudo cp -a "$SRC"/. "$DEST"/
sudo chown -R www-data:www-data "$DEST"
sudo find "$DEST" -type d -exec chmod 755 {} \;
sudo find "$DEST" -type f -exec chmod 644 {} \;
sudo chmod +x "$DEST/build_manifest.py" "$DEST/publish.sh" "$DEST/install_vps.sh"
echo "Installed to $DEST"
echo "Test: curl http://127.0.0.1/aujunpeak-data/manifest.json"
