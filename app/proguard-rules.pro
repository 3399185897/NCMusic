# Retrofit / OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn retrofit2.**

# kotlinx.serialization 生成的序列化器
-keepclassmembers class com.buddy.ncmusic.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.buddy.ncmusic.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coil
-dontwarn coil.**

# Media3
-dontwarn androidx.media3.**
