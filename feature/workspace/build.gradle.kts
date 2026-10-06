plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.workspace"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":runtime"))
    implementation(project(":project-template"))
    implementation(project(":tools"))
    implementation(project(":harness"))
    implementation(project(":feature:theme"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // LocalLiquidGlassBackdrop 的类型 LayerBackdrop 来自该库，类型推断需要它在 classpath 上
    implementation(libs.backdrop)

    testImplementation(libs.junit)
}
