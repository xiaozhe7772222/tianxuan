plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.onboarding"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    implementation(project(":tools"))
}
