# Add project specific ProGuard rules here.
# Keep data model classes for Gson
-keep class com.mlbbassistant.data.model.** { *; }
-keep class com.mlbbassistant.data.api.dto.** { *; }
-keep class com.mlbbassistant.data.db.entity.** { *; }

# Keep Hilt generated classes
-keep class * extends dagger.hilt.android.internal.managers.ApplicationComponentManager { *; }
