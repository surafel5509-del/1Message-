# Keep Room generated implementations reachable via reflection.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Hilt / Dagger metadata
-keep class dagger.hilt.** { *; }
-keepclassmembers class * { @javax.inject.Inject <init>(...); }
-dontwarn dagger.hilt.**

# Keep enum names used for persistence of preferences
-keepclassmembers enum com.onemessages.app.** { *; }

# Crash reports must never contain message bodies; we strip Throwable messages
# in our own logger, so keep line numbers for debugging.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
