# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**

# Hilt
-keep class dagger.hilt.** { *; }

# kotlinx.serialization
-keepclassmembers,allowshrinking,includedescriptorclasses class * {
    @kotlinx.serialization.Serializable <fields>;
}