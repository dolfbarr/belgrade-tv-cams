# Keep kotlinx.serialization descriptors for CatalogManifest
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class rs.belgrade.tvcams.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class rs.belgrade.tvcams.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
