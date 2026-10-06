import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Properties
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 语义化版本号：x.y.z，可带 -rc1 / -dev 之类预发布后缀。
// 不用 \d+\.\d+\.\d+ 之外的花样，是为了让客户端的 parts() 分段比较有确定语义。
val SEMVER_PATTERN = Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?""")

// 版本号真源在根 gradle.properties（tianxuan.versionName / tianxuan.versionCode）。
// 曾经写死在这里，而 release.sh 另收一个位置参数、CI 再 grep 本文件反解一次，
// 三处独立来源会各自漂移——最坏情况是构建出 versionName 与 tag 不符的 APK，
// 客户端「检查更新」按 versionName 语义比对，会判定不出该有的更新。
val appVersionName: String = providers.gradleProperty("tianxuan.versionName").orNull?.trim().orEmpty()
val appVersionCodeRaw: String = providers.gradleProperty("tianxuan.versionCode").orNull?.trim().orEmpty()

require(SEMVER_PATTERN.matches(appVersionName)) {
    "tianxuan.versionName 必须形如 0.21.0（x.y.z，可带 -rc1 预发布后缀），实际为 \"$appVersionName\""
}
val appVersionCode: Int = appVersionCodeRaw.toIntOrNull()
    ?: error("tianxuan.versionCode 必须是正整数，实际为 \"$appVersionCodeRaw\"")
require(appVersionCode > 0) {
    "tianxuan.versionCode 必须为正整数（Android 用它判断能否覆盖安装），实际为 $appVersionCode"
}

// 是否对 release 变体启用 R8 混淆与资源压缩。
// 内测阶段为 false —— 混淆会让「崩溃堆栈不可读」，内测期排障本就依赖可读堆栈，
// 关掉同时也把构建时间从十分钟级压回分钟级。对外正式发布前改回 true 即可。
val tianxuanMinify: Boolean = providers.gradleProperty("tianxuan.minify")
    .orNull?.trim()?.toBooleanStrictOrNull()
    ?: false

// release 变体是否带 LeakCanary。内测期 true，正式对外发布前应置 false——
// 它会在应用内弹窗展示泄漏堆栈，属于开发工具，不该出现在用户手机上。
val tianxuanLeakCanary: Boolean = providers.gradleProperty("tianxuan.leakcanary")
    .orNull?.trim()?.toBooleanStrictOrNull()
    ?: true

// TianXuanDev 双包构建开关：CI（.github/workflows/tianxuandev-build.yml）设 TIANXUAN_DEV_BUILD=1 时，
// 产出独立预览包 top.wkbin.tianxuan.dev / 应用名 TianXuanDev / 版本后缀 -dev，
// 与正式版（top.wkbin.tianxuan）及本地调试包（top.wkbin.tianxuan.debug）完全共存互不干扰。
val tianXuanDevBuild = System.getenv("TIANXUAN_DEV_BUILD") == "1"

plugins {
    alias(libs.plugins.tianxuan.android.application)
    alias(libs.plugins.tianxuan.android.application.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

extensions.configure<ApplicationExtension> {
    namespace = "top.wkbin.tianxuan"
    resourcePrefix = "tianxuan_"
    ndkVersion = "30.0.15729638"

    defaultConfig {
        applicationId = if (tianXuanDevBuild) "top.wkbin.tianxuan.dev" else "top.wkbin.tianxuan"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        // 应用名统一走 manifest placeholder：TianXuanDev 构建显示 "TianXuanDev"，其余显示 "天玄"。
        manifestPlaceholders["appLabel"] = if (tianXuanDevBuild) "TianXuanDev" else "天玄"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?: project.file("keystore.properties").takeIf { it.exists() }
    val keystoreProperties = Properties()
    if (keystorePropertiesFile != null) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    fun signingValue(environmentVariable: String, propertyName: String): String? =
        System.getenv(environmentVariable)?.takeIf { it.isNotBlank() }
            ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

    val signingStoreFilePath = signingValue("TIANXUAN_RELEASE_STORE_FILE", "storeFile")
    val signingStorePassword = signingValue("TIANXUAN_RELEASE_STORE_PASSWORD", "storePassword")
    val signingKeyAlias = signingValue("TIANXUAN_RELEASE_KEY_ALIAS", "keyAlias")
    val signingKeyPassword = signingValue("TIANXUAN_RELEASE_KEY_PASSWORD", "keyPassword")
    val signingValues = listOf(
        signingStoreFilePath,
        signingStorePassword,
        signingKeyAlias,
        signingKeyPassword,
    )
    //noinspection WrongGradleMethod
    val signingRequested = signingValues.any { it != null }
    //noinspection WrongGradleMethod
    val signingConfigured = signingValues.all { it != null }
    check(!signingRequested || signingConfigured) {
        "Release signing is only partially configured. Provide all TIANXUAN_RELEASE_* environment variables " +
            "or all entries in keystore.properties."
    }

    signingConfigs {
        create("release") {
            if (signingConfigured) {
                val storeFilePath = requireNotNull(signingStoreFilePath)
                val resolvedStoreFile = if (storeFilePath.startsWith("/") || storeFilePath.contains(":\\")) {
                    file(storeFilePath)
                } else {
                    rootProject.file(storeFilePath).takeIf { it.exists() } ?: project.file(storeFilePath)
                }
                storeFile = resolvedStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                // v3 签名（密钥轮换支持）。minSdk 29 只需 v2，但 v3 让日后更换密钥时
                // 新旧包可共存安装，不必强制用户先卸载——内测期就把这个能力装上，
                // 免得真要轮换密钥时才发现签名方案没跟上。
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // TianXuanDev 双包构建：包名与应用名已在 defaultConfig 按 tianXuanDevBuild 分流，
            // 此处只控制后缀——本地调试包保持 top.wkbin.tianxuan.debug/-debug，
            // TianXuanDev 预览包（top.wkbin.tianxuan.dev）不再叠加额外后缀，版本后缀为 -dev。
            if (!tianXuanDevBuild) {
                applicationIdSuffix = ".debug"
            }
            versionNameSuffix = if (tianXuanDevBuild) "-dev" else "-debug"
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            manifestPlaceholders["appLabel"] = if (tianXuanDevBuild) "TianXuanDev" else "天玄"
            // 内测阶段不开R8 混淆与资源压缩。
            //
            // 关闭的是「防反编译」，不是签名：签名是 Android 的安装前提，
            // 与保密无关，下面照样用 release keystore 签。
            //
            // 为什么仍要走 release 变体而不是直接发 debug 包：
            // debug 带 applicationIdSuffix=".debug"，包名是 top.wkbin.tianxuan.debug，
            // 与正式包 top.wkbin.tianxuan 是两个不同应用。用户先装内测包再装正式版时，
            // 会出现两个「天玄」并存、互相抢无障碍与输入法权限，且无法覆盖升级。
            //
            // 开关放在 gradle.properties：正式对外发布前把 tianxuan.minify 置 true 即可
            // 恢复混淆（proguard 规则已就绪，无需再改代码）。
            isMinifyEnabled = tianxuanMinify
            isShrinkResources = tianxuanMinify
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            // PRoot is launched as an extracted ARM64 executable on Android 10+.
            useLegacyPackaging = true
            // TianXuan only supports arm64-v8a; Android AARs may also publish legacy/x86 ABIs.
            excludes += listOf(
                "**/armeabi-v7a/*.so",
                "**/x86/*.so",
                "**/x86_64/*.so",
            )
            // The PRoot tracee loader is an executable payload, not a JNI library.
            // Preserve the official package bytes instead of running AGP's strip tool.
            keepDebugSymbols += "**/libproot-loader.so"
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/license.txt",
                "META-INF/notice.txt"
            )
        }
    }
}

dependencies {
    // Baseline Profile 运行时安装器：首帧前将打包进 APK 的 profile 提交给 ART 预编译。
    implementation(libs.androidx.profileinstaller)
    // 生成者模块：generateBaselineProfile 时由此拉起 macrobenchmark 采集
    baselineProfile(project(":baselineprofile"))
}

dependencies {
    // LeakCanary：内测期是排障主力（内存泄漏在平板上比在桌面更难复现与定位）。
    // debug 构建恒开；release 由 tianxuan.leakcanary 控制，
    // 因为内测包走 release 变体，若只在 debug 声明，内测用户反而拿不到泄漏报告。
    debugImplementation(libs.leakcanary.android)
    if (tianxuanLeakCanary) {
        releaseImplementation(libs.leakcanary.android)
    }
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":core:datastore"))
    implementation(project(":runtime"))
    implementation(project(":project-template"))
    implementation(project(":tools"))
    implementation(project(":harness"))
    implementation(project(":feature:components"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:workflow"))
    implementation(project(":feature:navigation"))
    implementation(project(":feature:custom_iteration"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:theme"))
    implementation(project(":feature:git"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.bundles.koin.compose)
    // 工作流定时计划：WorkManager 到点触发 + Koin Worker 注入
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.koin.workmanager)

    implementation(libs.bundles.room)

    implementation(libs.shizuku.provider)

    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
    // Android must use the AAR; the default JVM JAR does not package Android JNI libraries.
    implementation(libs.zstd) {
        artifact { type = "aar" }
    }
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.bundles.test.robolectric)
    testImplementation(libs.koin.test)
    testImplementation(libs.androidx.work.testing)
    // Robolectric on Java 25 requires the same ASM override as core:database.
    testImplementation(libs.bundles.asm.test)
}

val bundledProot = layout.projectDirectory.file(
    "src/main/jniLibs/arm64-v8a/libproot.so",
)
val bundledProotLoader = layout.projectDirectory.file(
    "src/main/jniLibs/arm64-v8a/libproot-loader.so",
)
val bundledPtyNative = layout.projectDirectory.file(
    "src/main/jniLibs/arm64-v8a/libpty_native.so",
)
val bundledRtk = layout.projectDirectory.file("src/main/assets/bin/rtk")
val bundledRtkSha256 = "ce9a4847940ea26169df818d6907cd99bac0257a59ed4cc6c4b647e41277ad94"

tasks.configureEach {
    if (name == "preBuild") {
        dependsOn(rootProject.tasks.named("architectureCheck"))
        doFirst {
            check(bundledProot.asFile.isFile && bundledProot.asFile.length() > 4096L) {
                "Missing ARM64 PRoot tracer. Run tools/prepare-proot-runtime.ps1 before building."
            }
            check(bundledProotLoader.asFile.isFile && bundledProotLoader.asFile.length() > 4096L) {
                "Missing ARM64 PRoot loader. Run tools/prepare-proot-runtime.ps1 before building."
            }
            if (bundledRtk.asFile.exists()) {
                check(bundledRtk.asFile.isFile && bundledRtk.asFile.length() > 1_000_000L) {
                    "Bundled RTK executable in app/src/main/assets/bin/rtk is corrupted or too small."
                }
                val rtkBytes = bundledRtk.asFile.readBytes()
                check(
                    rtkBytes.size >= 20 &&
                        rtkBytes[0] == 0x7F.toByte() && rtkBytes[1] == 'E'.code.toByte() &&
                        rtkBytes[2] == 'L'.code.toByte() && rtkBytes[3] == 'F'.code.toByte() &&
                        rtkBytes[18] == 0xB7.toByte() && rtkBytes[19] == 0x00.toByte(),
                ) {
                    "Bundled RTK must be an ELF AArch64 Linux executable."
                }
                val rtkSha256 = MessageDigest.getInstance("SHA-256")
                    .digest(rtkBytes)
                    .joinToString("") { "%02x".format(it) }
                check(rtkSha256 == bundledRtkSha256) {
                    "Bundled RTK SHA-256 mismatch. Expected $bundledRtkSha256, got $rtkSha256."
                }
            }
            // libpty_native 必须是 NDK/Bionic 构建：若依赖 glibc 的 libc.so.6，设备上
            // dlopen 必失败并静默回退到 script PTY 路径（PTY 回显问题会随之复发）。
            val ptyNativeBytes = bundledPtyNative.asFile.readBytes()
            val glibcMarker = "libc.so.6".toByteArray()
            check(ptyNativeBytes.size < glibcMarker.size ||
                (0..ptyNativeBytes.size - glibcMarker.size).none { offset ->
                    glibcMarker.indices.all { ptyNativeBytes[offset + it] == glibcMarker[it] }
                }) {
                "libpty_native.so is linked against glibc (libc.so.6). Rebuild it with the NDK " +
                    "aarch64-linux-android clang (see app/src/main/cpp/CMakeLists.txt)."
            }
        }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants { variant ->
        val buildAppName = "tianxuan-v${appVersionName}-${variant.name}.apk"
        variant.outputs.forEach { output ->
            output.outputFileName.set(buildAppName)
        }
    }
}
