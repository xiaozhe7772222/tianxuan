plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.preview"
}

dependencies {
    implementation(project(":feature:theme"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.backdrop)
}
