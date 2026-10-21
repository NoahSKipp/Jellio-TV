# Proguard / R8 rules for Jellio TV release optimization

# Preserve Moshi models and generated adapters
-keep class com.jellio.tv.data.model.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-dontwarn com.squareup.moshi.**

# Preserve Retrofit interfaces and service methods
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# Coroutines and OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.coroutines.**

# Compose & TV Material
-keep class androidx.compose.** { *; }
-keep class androidx.tv.material3.** { *; }

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
