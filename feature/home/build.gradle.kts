plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.home"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    implementation(libs.androidx.activity.compose)
}
