plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.settings"
}

dependencies {
    implementation(project(":feature:theme"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":runtime"))
    implementation(project(":tools"))
    implementation(project(":harness"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.kotlinx.serialization.json)
    // LocalLiquidGlassBackdrop 的类型 LayerBackdrop 来自该库，类型推断需要它在 classpath 上
    implementation(libs.backdrop)
    implementation(libs.bundles.coil)

    testImplementation(libs.junit)
}
