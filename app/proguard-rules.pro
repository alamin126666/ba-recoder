# BA Screen Record - Full R8 Protection & Size Optimization Rules

# Repackage and obfuscate internal classes aggressively
-repackageclasses 'o'
-allowaccessmodification
-optimizationpasses 5

# Strip source file names & debug logs in release builds
-renamesourcefileattribute SourceFile
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Keep Android entry points declared in AndroidManifest.xml
-keep public class com.example.BARecorderApp
-keep public class com.example.MainActivity
-keep public class com.example.service.ScreenRecordService
-keep public class com.example.service.DrawOverlayService

# MediaRecorder / MediaProjection are platform APIs reached through framework callbacks
-dontwarn android.media.MediaRecorder
-dontwarn android.media.projection.**

# Keep enum valueOf/values used by DataStore deserialisation
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Room database & entities / DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
    <init>(...);
}
-keep @androidx.room.Dao interface *

# Keep DataStore protobuf / preferences runtime classes
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Keep Kotlin Coroutines & Flow internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
