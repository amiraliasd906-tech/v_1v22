# This file was missing from the project even though app/build.gradle.kts's
# release buildType references it (proguardFiles(..., "proguard-rules.pro")),
# which would fail any release/minified build ("proguard-rules.pro (No such
# file or directory)"). These are safe, standard rules for the libraries this
# app actually uses with minifyEnabled/isShrinkResources on.

# --- kotlinx.serialization ---
# Keep the generated serializer() companions/classes for every @Serializable
# model used for the Anthropic API request/response bodies (OwnerDetector,
# ListingAiAssistant) and the WebView JS-bridge payloads (ExtractedListing).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.divarsmartsearch.app.**$$serializer { *; }
-keepclassmembers class com.divarsmartsearch.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.divarsmartsearch.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ---
# Entities/DAOs are referenced through generated code (KSP) and reflection
# for schema validation; keep the entity/database classes themselves.
-keep class com.divarsmartsearch.app.data.local.entity.** { *; }
-keep class com.divarsmartsearch.app.data.local.AppDatabase { *; }

# --- OkHttp / Okio (used by ListingDetailFetcher, OwnerDetector's direct
# Anthropic API call, and ListingAiAssistant) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
