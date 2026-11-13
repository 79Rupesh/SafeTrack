# SafeTrack ProGuard Rules
-keepattributes *Annotation*
-keepattributes Signature
-keepclassmembers class * {
    @org.jetbrains.annotations.* <fields>;
    @org.jetbrains.annotations.* <methods>;
}

# Firebase Realtime Database Models
-keepclassmembers class com.safetrack.data.model.** {
  !static !transient <fields>;
  <methods>;
}
-keep class com.safetrack.data.model.** { *; }
-keep class com.safetrack.database.entity.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Play Services Location & Maps
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.**
