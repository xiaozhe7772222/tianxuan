plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.browser"
    resourcePrefix = "fbrowser_"
}

dependencies {
    implementation(project(":core:browser"))
    implementation(project(":core:datastore"))
    implementation(project(":runtime:browser"))
    implementation(project(":feature:theme"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.coil)
}
