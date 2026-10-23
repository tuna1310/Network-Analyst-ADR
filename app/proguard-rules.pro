# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# dnsjava
-keep class org.xbill.DNS.** { *; }
-dontwarn org.xbill.DNS.**

# Hilt
-keep class * extends dagger.hilt.android.HiltAndroidApp
