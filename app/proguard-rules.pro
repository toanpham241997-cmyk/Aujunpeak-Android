##############################################################
# AUJUNPEAK VIP — ProGuard / R8 Rules
# Phiên bản: Maximum Protection
# Cập nhật: 2026-07
##############################################################

# ═══════════════════════════════════════════════════════════
# 1. R8 FULL MODE — Tối ưu mạnh nhất
# ═══════════════════════════════════════════════════════════
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses ''
-flattenpackagehierarchy ''

# ═══════════════════════════════════════════════════════════
# 2. XÓA LOG & DEBUG HOÀN TOÀN trong release
# ═══════════════════════════════════════════════════════════
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static java.lang.String getStackTraceString(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static void println(...);
    public static void print(...);
}

# ═══════════════════════════════════════════════════════════
# 3. GIỮ CÁC CLASS BẮT BUỘC — AndroidManifest entries
# ═══════════════════════════════════════════════════════════

# Application
-keep class com.aujunpeak.app.AujunpeakApp { *; }

# Activities — phải giữ nguyên tên vì AndroidManifest reference
-keep class com.aujunpeak.app.activities.SplashActivity { *; }
-keep class com.aujunpeak.app.activities.MainActivity { *; }
-keep class com.aujunpeak.app.activities.GameBoostActivity { *; }
-keep class com.aujunpeak.app.activities.OptimizerSuperActivity { *; }
-keep class com.aujunpeak.app.activities.CrosshairActivity { *; }
-keep class com.aujunpeak.app.activities.ForceUpdateActivity { *; }
-keep class com.aujunpeak.app.activities.UpdateActivity { *; }
-keep class com.aujunpeak.app.activities.SocialLinksActivity { *; }
-keep class com.aujunpeak.app.activities.AboutActivity { *; }
-keep class com.aujunpeak.app.activities.FeedbackActivity { *; }
-keep class com.aujunpeak.app.activities.PolicyActivity { *; }

# Services
-keep class com.aujunpeak.app.activities.MyFirebaseMessagingService { *; }
-keep class com.aujunpeak.app.activities.FloatingCrosshairService { *; }
-keep class com.aujunpeak.app.activities.FloatingBoostService { *; }
-keep class com.aujunpeak.app.utils.HeartbeatService { *; }

# BroadcastReceiver
-keep class com.aujunpeak.app.activities.BootReceiver { *; }

# ═══════════════════════════════════════════════════════════
# 4. GIỮ CLASS SECURITY — quan trọng, không được xóa
# ═══════════════════════════════════════════════════════════
-keep class com.aujunpeak.app.security.AppIntegrityChecker { *; }

# ═══════════════════════════════════════════════════════════
# 5. GIỮ FRAGMENTS
# ═══════════════════════════════════════════════════════════
-keep class com.aujunpeak.app.fragments.** { *; }

# ═══════════════════════════════════════════════════════════
# 6. KOTLIN — Giữ metadata tối thiểu
# ═══════════════════════════════════════════════════════════
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

# Giữ Kotlin coroutines internals
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.** {
    *;
}

# ═══════════════════════════════════════════════════════════
# 7. ANDROID FRAMEWORK — Không được obfuscate
# ═══════════════════════════════════════════════════════════

# Views được inflate từ XML — phải giữ constructor
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Custom views trong app
-keep class com.aujunpeak.app.views.** { *; }

# ViewBinding generated classes
-keep class com.aujunpeak.app.databinding.** { *; }
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static * bind(android.view.View);
}

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ═══════════════════════════════════════════════════════════
# 8. NAVIGATION COMPONENT
# ═══════════════════════════════════════════════════════════
-keepnames class androidx.navigation.fragment.NavHostFragment
-keepnames class * extends androidx.fragment.app.Fragment
-keep class androidx.navigation.** { *; }
-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public <init>();
}

# ═══════════════════════════════════════════════════════════
# 9. FIREBASE & GOOGLE SERVICES
# ═══════════════════════════════════════════════════════════
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.gms.** { *; }
-keepattributes *Annotation*

# Firebase Messaging
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }

# Google Sign-In
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ═══════════════════════════════════════════════════════════
# 10. GLIDE
# ═══════════════════════════════════════════════════════════
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}
-dontwarn com.bumptech.glide.**

# ═══════════════════════════════════════════════════════════
# 11. LOTTIE
# ═══════════════════════════════════════════════════════════
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ═══════════════════════════════════════════════════════════
# 12. WORKMANAGER
# ═══════════════════════════════════════════════════════════
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.aujunpeak.app.utils.HeartbeatScheduler { *; }

# ═══════════════════════════════════════════════════════════
# 13. ANDROIDX CORE & APPCOMPAT
# ═══════════════════════════════════════════════════════════
-keep class androidx.core.app.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.constraintlayout.** { *; }
-dontwarn androidx.**

# ═══════════════════════════════════════════════════════════
# 14. MATERIAL DESIGN
# ═══════════════════════════════════════════════════════════
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ═══════════════════════════════════════════════════════════
# 15. JSON PARSING — Giữ fields để không bị obfuscate khi parse
# ═══════════════════════════════════════════════════════════
-keep class org.json.** { *; }

# Data classes giao tiếp với API server (JSON serialization)
# Obfuscate tên class nhưng giữ tên fields để parse JSON đúng
-keepclassmembers class com.aujunpeak.app.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ═══════════════════════════════════════════════════════════
# 16. NETWORK — HttpURLConnection & OkHttp
# ═══════════════════════════════════════════════════════════
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.**
-keep class javax.net.ssl.** { *; }
-keep class java.security.** { *; }
-keep class sun.security.** { *; }

# ═══════════════════════════════════════════════════════════
# 17. BỎ QUA WARNING KHÔNG QUAN TRỌNG
# ═══════════════════════════════════════════════════════════
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.jetbrains.**
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.Unsafe
-dontwarn java.nio.file.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ═══════════════════════════════════════════════════════════
# 18. NATIVE LIBRARIES
# ═══════════════════════════════════════════════════════════
-keepclasseswithmembernames class * {
    native <methods>;
}

# ═══════════════════════════════════════════════════════════
# 19. OBFUSCATION DICTIONARY — Dùng ký tự giống nhau để khó đọc
# ═══════════════════════════════════════════════════════════
# Uncomment nếu bạn có file từ điển riêng:
# -obfuscationdictionary obfuscation-dictionary.txt
# -classobfuscationdictionary class-dictionary.txt
# -packageobfuscationdictionary package-dictionary.txt

# ═══════════════════════════════════════════════════════════
# 20. OUTPUT — Giữ mapping file để debug crash từ production
# ═══════════════════════════════════════════════════════════
-printmapping mapping.txt
-printseeds seeds.txt
-verbose

# ── Dynamic UI/data updater ───────────────────────────────────────────────
# WorkManager creates the worker by class name. Keep its constructor/name stable.
-keep class com.aujunpeak.app.dynamicui.DynamicUiWorker { *; }
-keepnames class com.aujunpeak.app.dynamicui.DynamicUiWorker
