package top.wkbin.tianxuan.runtime

import java.io.File

/**
 * APK 逆向工程指引的生成。
 *
 * 从 WorkspaceManager 抽出来：这段是**纯文档模板**（jadx / apktool 命令、
 * 加固壳特征表、脱壳方案表），不碰任何运行期状态，却占了 87 行，
 * 把 WorkspaceManager 的纵向尺寸推过了架构棘轮基线。
 *
 * 抽出的好处不止行数：WorkspaceManager 负责目录与元数据事务，
 * 文档模板的措辞改动不该牵动那个文件，也不该让它的 review diff 变大。
 *
 * 生成逆向工作流指引 README，衔接天玄内置的 jadx / apktool / 逆向 MCP 能力。
 */
internal fun writeReverseReadme(
    projectDir: File,
    name: String,
    apkFileName: String,
    unpackedDir: File,
    sourceLabel: String,
) {
    val entryCount = unpackedDir.walkTopDown().count { it.isFile }
    File(projectDir, "REVERSE.md").writeText(
        """
        # $name · APK 逆向工程

        > 来源：$sourceLabel
        > 导入时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}

        ## 工程结构

        | 路径 | 说明 |
        | :--- | :--- |
        | `$apkFileName` | 原始安装包（未改动） |
        | `unpacked/` | 第一层 ZIP 解包产物（$entryCount 个文件）：`classes.dex`、`resources.arsc`、`AndroidManifest.xml`（二进制 AXML）、`res/`、`assets/`、`lib/` 等 |
        | `apk-info.properties` | 来源与元数据 |

        ## 下一步：在天玄终端 / Agent 中继续深挖

        沙箱内已内置逆向工具链（Android & 移动全栈开发套件 或 apktool 套件装配后可用）：

        ```bash
        # 1) DEX -> Java 源码（推荐，可读性最好）
        jadx -d java-src "$apkFileName"

        # 2) 完整解包资源 + Smali（可回编译）
        apktool d "$apkFileName" -o apktool-out
        #   回编译：apktool b apktool-out -o rebuilt.apk

        # 3) 二进制清单解码（配合 apktool 产物）
        #    aapt dump badging "$apkFileName"   # 包名 / 版本 / 权限
        #    aapt dump xmltree "$apkFileName" AndroidManifest.xml
        ```

        Agent 对话中还可启用内置 **Android 逆向 MCP 服务**（`mcp_apktool`，在 MCP 设置中开启）：
        `decode_apk` / `analyze_manifest` / `extract_strings` / `search_smali` / `build_apk` / `sign_apk`。

        ## 分析关注点

        - **AndroidManifest.xml**：四大组件导出状态、权限声明、Application 类
        - **classes.dex**：核心业务逻辑（jadx 反编译后检索 URL / 密钥 / 加解密特征）
        - **lib/**：native .so（可用 IDA / 玄星逆核 SOMCP 深度分析）
        - **assets/** 与 **res/**：内置资源、配置文件、可能存在的加固壳特征

        > 提示：如果打开 `unpacked/AndroidManifest.xml` 是乱码，属正常现象（AXML 二进制格式），
        > 用 `apktool d` 或 `aapt dump xmltree` 解码即可。

        ## 识别加固壳（jadx 打开看不到真实代码时）

        若 `unpacked/classes.dex` 反编译后只有壳的 stub 加载器，说明 APK 被加固。看 `lib/` 下的 so 名最快定位厂商：

        | 特征 so | 加固厂商 |
        | :--- | :--- |
        | `libjiagu.so` / `libjiagu_art.so` | **360 加固**（入口 `com.stub.StubApp`） |
        | `libDexHelper.so` / `libSecShell.so` / `libsecexe.so` | **梆梆（SecNeo/Bangcle）**（入口 `com.secneo.apkwrapper.ApplicationWrapper`） |
        | `libshellx-super*.so` / `libtup.so` / `libexec.so` | **腾讯乐固 / 御安全**（`com.tencent.StubShell`） |
        | `libnesec.so` | **网易易盾**（`com.netease.nis.wrapper`） |
        | `ijiami.ajm` / `libexecmain.so` / `assets/ijm_lib/` | **爱加密**（入口 `s.h.e.l.l.S`） |
        | `libbaiduprotect.so` / `assets/baiduprotect*` | **百度加固** |
        | `libzuma.so` / `assets/qihoo/` | **阿里聚安全** |
        | `libddog.so` / `libchaosvmp.so` | **娜迦（Nagain，VMP 壳）** |
        | `libx3g.so` | **顶像** |
        | `libkwscmm.so` / `libkwsgmain.so` | **几维** |
        | `libnqshield.so` / `libmobisec.so` / `libkiroro.so` | 网秦 / 阿里旧版 / Kiro 等 |

        辅助判据：`assets/` 下的特征文件（`ijiami.dat`、`bangcleplugin/`、`libjiagu*`、`appsealing*`），以及 AndroidManifest 入口 `android:name`。

        ## 遇到加固壳：脱壳指引

        | 壳级别 | 特征 | 脱壳方案 |
        | :--- | :--- | :--- |
        | **一代壳**（整体 dex 加密） | jadx 只能看到 stub | **通用脱壳**：FRIDA-DEXDump（`frida -U -f 包名 -l frida-dexdump.js`）、BlackDex / FullDump（免 root 一键）、MT 管理器脱壳插件 |
        | **二代壳**（方法抽取 / 函数抽取） | 方法体运行时回填 | **主动调用脱壳**：FART / Youpk / 反射大师（定制 ROM 或 Xposed 级框架触发每个方法回填后再 dump） |
        | **VMP 壳**（指令虚拟化，如娜迦 chaosvmp） | 代码被虚拟化保护 | 极难整体脱，通常只能**动态调试关键逻辑**（Frida hook / Unidbg 模拟执行） |

        脱壳后处理：dump 出的 `classesN.dex` 可能头部/校验被破坏 → 修复 dex header 后再 `jadx` 反编译；若要改逻辑，多数壳允许在原 APK 对应 smali/so 上 patch 后重打包。
        """.trimIndent() + "\n",
        Charsets.UTF_8,
    )
}
