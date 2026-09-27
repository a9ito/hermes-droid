# Hermes Droid — R8/ProGuard rules for release builds.
#
# The app is small and mostly Compose + Retrofit + kotlinx.serialization. The
# rules below keep the pieces that reflection-based / codegen libraries need.

# --- kotlinx.serialization -----------------------------------------------------
# Keep @Serializable classes' synthetic companion + serializer, and the generated
# serializers themselves. Without these, R8 can strip serializers and cause
# runtime SerializationException on release builds.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the generated serializer for every @Serializable type in our model package.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Our DTOs live here; keep them intact so field names survive for (de)serialization.
-keep,includedescriptorclasses class com.a9ito.hermesagent.data.remote.dto.** { *; }

# --- Retrofit / OkHttp ---------------------------------------------------------
# Retrofit does reflection on generic parameters and annotations.
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# OkHttp platform code references optional runtime classes; silence the warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coroutines
-dontwarn kotlinx.coroutines.**
