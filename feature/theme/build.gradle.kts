plugins {
    alias(libs.plugins.tianxuan.android.library.compose)
}

android {
    namespace = "top.wkbin.tianxuan.feature.theme"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.compose.ui)
    // AndroidLiquidGlass (Kyant0)：澄明(液态玻璃)主题的毛玻璃折射效果
    implementation(libs.backdrop)
    implementation(libs.kotlinx.coroutines.core)
}
