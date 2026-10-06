plugins {
    alias(libs.plugins.tianxuan.android.library)
}

android {
    namespace = "top.wkbin.tianxuan.harness"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:browser"))
    api(project(":runtime:browser"))
    implementation(project(":core:model"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":runtime"))
    implementation(project(":tools"))
    implementation(libs.koin.core)
    implementation(libs.okhttp)
    implementation(libs.bundles.ktor.server)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bundles.test.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // 集成测试需要直接构建 in-memory Room 数据库（core:database 是 implementation 依赖不传递 Room）
    testImplementation(libs.bundles.room)
    testImplementation(libs.bundles.asm.test)
}
