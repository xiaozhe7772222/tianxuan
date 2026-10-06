plugins {
    alias(libs.plugins.tianxuan.android.library)
}

android {
    namespace = "top.wkbin.tianxuan.core.datastore"
}

dependencies {
    implementation(project(":core:security"))
    implementation(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
