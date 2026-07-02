# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers for stack traces in play store console reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ZXing QR Engine Keep Rules
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Room Database Keep Rules
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-dontwarn androidx.room.**

# WorkManager Proguard Rules to prevent NoSuchMethodException on startup
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>(...);
}
-keep class * extends androidx.work.Worker {
    <init>(...);
}
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}
-dontwarn androidx.work.**

# Osmdroid (OpenStreetMap) Proguard Rules
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Jetpack Compose and Kotlin serialization
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}