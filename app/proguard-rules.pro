# Keep kotlinx.serialization descriptors and generated serializers for CatalogManifest & wire models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class rs.belgrade.tvcams.domain.**$$serializer { *; }
-keepclassmembers class rs.belgrade.tvcams.domain.** {
    *** Companion;
}
-keepclasseswithmembers class rs.belgrade.tvcams.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio platform reflection warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
