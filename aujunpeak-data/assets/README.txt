Put remote UI images/GIF/WebP files in this folder, then run:

    python3 build_manifest.py

The app downloads only the files listed in manifest.json and verifies SHA-256.
To use an asset from ui.json on an ImageView, add for example:

  "appBackgroundImage": { "asset": "background_v2.gif", "scaleType": "centerCrop" }

Supported data assets are visual/static data. Do not place APK/DEX/JAR/SO executables here.
