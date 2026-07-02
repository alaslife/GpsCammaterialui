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
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Jetpack Compose and Kotlin serialization
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}