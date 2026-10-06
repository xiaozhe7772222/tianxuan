plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.chat"
}

dependencies {
    implementation(project(":feature:theme"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    implementation(project(":harness"))
    implementation(project(":tools"))
    // A2UI PoC：render_surface 工具结果在聊天流内嵌渲染原生界面
    implementation(project(":feature:a2uipoc"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    // LocalLiquidGlassBackdrop 的类型 LayerBackdrop 来自该库，类型推断需要它在 classpath 上
    implementation(libs.backdrop)
    implementation(libs.bundles.coil)

    testImplementation(libs.bundles.test.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // ui-test-manifest 必须进 debugImplementation 而非 testImplementation：
    // 它提供的是被测组件所需的 Activity 清单，放到 test 作用域时测试环境里拿不到。
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
