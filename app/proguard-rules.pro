# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# dnsjava & SLF4J
-keep class org.xbill.DNS.** { *; }
-dontwarn org.xbill.DNS.**
-dontwarn org.slf4j.**
-dontwarn java.net.spi.**
-dontwarn sun.net.spi.nameservice.**

# Hilt
-keep class * extends dagger.hilt.android.HiltAndroidApp
-dontwarn dagger.hilt.**
