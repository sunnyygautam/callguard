# CallGuard R8 / ProGuard Configuration

# Preserve obfuscated stack trace line numbers while hiding local source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Room database, generated implementation, DAOs, and entity constructors.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep Telecom services, activities, and broadcast receivers referenced via AndroidManifest.
-keep class com.example.telecom.CallGuardInCallService { *; }
-keep class com.example.telecom.PrefixCallScreeningService { *; }
-keep class com.example.telecom.CallActionReceiver { *; }
-keep class com.example.ui.incall.InCallActivity { *; }

# Strip verbose and debug log calls in release builds to reduce DEX size and runtime string overhead.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
