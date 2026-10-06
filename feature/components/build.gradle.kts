plugins {
    alias(libs.plugins.tianxuan.android.library.compose)
}

android {
    namespace = "top.wkbin.tianxuan.feature.components"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":feature:theme"))
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    // 澄明(液态玻璃)主题：底部导航毛玻璃折射
    implementation(libs.backdrop)
    implementation(libs.bundles.coil)

    // 宽度分型（平板三栏的断点判定）是纯函数，必须有测试锁住边界值：
    // 判错不会抛异常，只会让平板上内容被挤没。
    testImplementation(libs.junit)
}
