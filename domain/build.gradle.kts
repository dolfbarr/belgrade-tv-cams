plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    val v1Catalog = rootProject.file("catalog/catalog.v1.json")
    val v2Catalog = rootProject.file("catalog/catalog.v2.json")
    inputs.files(v1Catalog, v2Catalog)
    systemProperty("belgrade.catalog.v1.path", v1Catalog.absolutePath)
    systemProperty("belgrade.catalog.v2.path", v2Catalog.absolutePath)
}
