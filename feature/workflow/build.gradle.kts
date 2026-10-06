plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.workflow"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":runtime"))
    implementation(project(":harness"))
    implementation(project(":feature:theme"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
