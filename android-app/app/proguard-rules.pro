# Add any ProGuard configurations here
-keep class com.adwatch.** { *; }
-keepattributes *Annotation*

# Keep Google Mobile Ads SDK
-keep class com.google.android.gms.ads.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }

# Keep Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
