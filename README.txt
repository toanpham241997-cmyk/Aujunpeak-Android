FINAL UI/GCM/STORAGE-SAFETY PATCH

Replaces:
- GameModuleInstaller.kt (fix AES-GCM constructor/runtime auth-tag handling; explicit Android 11 protected-path handling)
- FunctionFragment.kt (red-only animation accents)
- Selected neo UI drawables (black + red, including bottom navigation: only top corners rounded)
- colors_final_override.xml (red/black compatibility aliases)

Important:
Android 11+ intentionally blocks third-party apps from directly writing another app's /Android/data directory. This patch does not attempt to bypass that platform boundary. It instead prevents false success and gives a precise target-directory error.
