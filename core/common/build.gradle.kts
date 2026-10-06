plugins {
    alias(libs.plugins.tianxuan.android.library)
}

android {
    namespace = "top.wkbin.tianxuan.core.common"
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mlkit.translate)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
