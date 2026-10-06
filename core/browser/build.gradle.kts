plugins {
    alias(libs.plugins.tianxuan.jvm.library)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
