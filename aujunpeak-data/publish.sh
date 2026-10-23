#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
python3 build_manifest.py --bump
chmod 644 manifest.json ui.json
find assets -type f -maxdepth 1 -exec chmod 644 {} \; 2>/dev/null || true
echo "Published. Check: http://103.140.249.74/aujunpeak-data/manifest.json"
