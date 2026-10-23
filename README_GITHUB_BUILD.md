# AUJUNPEAK VN — BUILD ANDROID APK TREN GITHUB

Source goc duoc giu nguyen (Kotlin, giao dien, tai nguyen, Firebase, app ID, backend/web-admin, versionCode=17, versionName=17.0).
Chi bo sung GitHub Actions va sua duong dan Java cuc bo de build tren runner Ubuntu.

## 1. Dua source len GitHub

1. Tao repository GitHub (nen chon **Private**, vi du an co phan quan tri may chu).
2. Giai nen file ZIP nay. Upload **cac file va thu muc NAM O GOC** cua thu muc da giai nen len GitHub; can co `app/`, `gradle/`, `gradlew`, `settings.gradle.kts` va `.github/workflows/android-build.yml` ngay tai goc repository.
   **Luu y:** Khi upload qua GitHub web, file `.github/workflows/android-build.yml` phai nam dung vi tri. Tot nhat push bang Git thay vi upload tung file.
3. Commit to `main` (hoac bat ky branch nao).

## 2. Build APK Debug — KHONG CAN KEYSTORE

- Moi lan `push`, GitHub Actions tu dong build APK Debug.
- Hoac vao repository -> **Actions** -> **Aujunpeak Android - Build APK** -> **Run workflow** -> build_type `debug` -> Run workflow.
- Khi run thanh cong, mo run -> **Artifacts** -> tai `Aujunpeak-DEBUG-APK.zip` -> giai nen de lay `app-debug.apk`.
- Ban Debug duoc ky boi Android debug key do GitHub tao, su dung de kiem tra / cai thu.

## 3. Build APK Release chinh thuc — CAN KEYSTORE GOC

**RAT QUAN TRONG:** Ma nguon goc co `AppIntegrityChecker` kiem tra fingerprint SHA-256 cua chung chi ky Release. De giu nguyen bao ve va hoat dong, PHAI su dung chinh file keystore goc truoc day. Khong tao file keystore moi neu muon giu nguyen chu ky.

Trong GitHub -> Settings -> Secrets and variables -> Actions -> New repository secret, them 4 secrets:

- `ANDROID_KEYSTORE_BASE64`: noi dung Base64 cua toan bo file `.jks` goc
- `ANDROID_KEYSTORE_PASSWORD`: mat khau file keystore
- `ANDROID_KEY_ALIAS`: alias key ky release
- `ANDROID_KEY_PASSWORD`: mat khau cua alias

Chuyen file JKS sang Base64 tren Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\duong-dan\aujunpeak.jks")) | Set-Clipboard
```

Lenh nay copy Base64 vao clipboard; dan vao secret `ANDROID_KEYSTORE_BASE64`. KHONG commit `.jks`, mat khau hay `keystore.properties` len GitHub.

Sau do: Actions -> Aujunpeak Android - Build APK -> Run workflow -> `release` -> Run workflow -> download artifact `Aujunpeak-RELEASE-APK.zip` -> `app-release.apk`.

Neu chua co keystore goc: hay build Debug. Release se dung neu khong du 4 secrets, hoac fingerprint khong trung voi ung dung goc (co chu dich de tranh build Release cai duoc nhung bi app tu dong dong do sai chu ky).

## 4. Cac van de thuong gap

- **Actions khong chay:** Kiem tra file `.github/workflows/android-build.yml` duoc push len branch dung va repository cho phep Actions.
- **Loi `Java home is invalid`:** Da khac phuc bang cach bo `org.gradle.java.home` tro toi may Windows. GitHub setup Java 17 tu dong.
- **Loi Gradle wrapper permission:** Workflow da chay `chmod +x ./gradlew`.
- **Loi `Missing GitHub Actions Secret`:** Dien du 4 secrets, hoac chon Debug.
- **Loi signing key fingerprint mismatch:** Dung file JKS goc da ky cac APK truoc day (khong phai keystore moi).
- **Cannot find Android SDK 34:** Workflow tu cai SDK 34 va build-tools 34.0.0.
- **Release build khong the cai de cap nhat APK cu:** Chu ky APK phai trung voi chu ky cua ban da cai; phai dung keystore goc.
- **Khong thay APK:** Mo Actions run -> Artifacts, khong tim trong root repo. File Artifact GitHub tai ve co dang ZIP, giai nen de lay APK.

## 5. Ghi chu ve giu nguyen du an

- KHONG thay doi Kotlin, AndroidManifest, giao dien, icons, anh, assets, backend, cau hinh Firebase hay logic cua app.
- KHONG doi package, version, minSdk, targetSdk, compileSdk, dependency hay fingerprint ky Release.
- Chi sua `gradle.properties` de loai bo JDK path cuc bo; bo sung workflow `.github/workflows/android-build.yml`, cap nhat `.gitignore` de tranh commit thong tin nhay cam, va huong dan nay.
- Cac thu muc/file tao khi build tren may (nhu `app/build/`, `.idea/`, `local.properties`, `app/release/`, `app/mapping.txt`, `app/seeds.txt`) khong duoc dua vao goi source GitHub vi duoc tai tao khi build. Source can de build duoc bao toan.
- Chua xac nhan build thanh cong tren runner cua GitHub cho toi khi ban push va chay workflow tai repository cua ban.
