plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.knowledge"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":harness"))
    implementation(project(":tools"))
    implementation(libs.bundles.compose.ui)
    implementation("androidx.compose.material:material-icons-core")
}
