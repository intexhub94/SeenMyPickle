# PBCam ProGuard Rules (2026 Golden Build)

# 1. Standard Android rules are already included via 'proguard-android-optimize.txt'

# 2. Project-Specific Obfuscation Hardening
# We want to obfuscate our security logic as much as possible.
-keep class com.pbcam.app.data.db.** { *; } # Keep Room entities for DB mapping
-keep class com.pbcam.app.data.OperationStates** { *; }
-keep class com.pbcam.app.data.CameraSource** { *; }

# Obfuscate SecurityUtils but keep names that might be needed by reflection (if any)
# Actually, better to obfuscate as much as possible.
-keepclassmembers class com.pbcam.app.data.SecurityUtils {
    public static ** getDeviceId(android.content.Context);
    public static ** verifyLicense(android.content.Context, java.lang.String);
}

# 3. Library Specific Keeps (to prevent crashes)
-keep class androidx.camera.** { *; }
-keep class androidx.media3.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.arthenica.ffmpegkit.** { *; }

# 4. Remove all Log.d and Log.v calls from release builds for security
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# 5. General Hardening
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
