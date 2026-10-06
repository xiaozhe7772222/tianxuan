plugins {
    alias(libs.plugins.tianxuan.android.library)
}

android {
    namespace = "top.wkbin.tianxuan.template"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.koin.core)
    implementation(libs.kotlinx.serialization.json)
}
