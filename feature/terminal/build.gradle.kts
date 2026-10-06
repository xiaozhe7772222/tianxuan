plugins {
    alias(libs.plugins.tianxuan.android.feature)
}

android {
    namespace = "top.wkbin.tianxuan.feature.terminal"
}

dependencies {
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    implementation(libs.androidx.activity.compose)
    // Termux TerminalView (pairs with runtime's terminal-emulator AAR).
    implementation(libs.termux.terminal.view)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
