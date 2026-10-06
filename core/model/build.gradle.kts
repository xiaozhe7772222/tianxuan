plugins {
    alias(libs.plugins.tianxuan.jvm.library)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
