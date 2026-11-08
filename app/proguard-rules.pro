# Keep Room entities and database classes
-keep class com.scrbl.app.data.local.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
