# 🛠️ 天玄 (TianXuan) — 常用开发与自动化命令速查 (Runbook & Commands)

---

## 1. 构建环境要求 (Prerequisites)

- **Android SDK**：主工程需要 Android SDK Platform 37.1（A2UI alpha 依赖要求）；targetSdk 仍为 37。已接受 SDK 许可证时，Gradle 可自动安装缺失的平台。

- **JDK 环境变量设置（Windows PowerShell）**：
  必须通过设置 `JAVA_HOME` 指向 Android Studio 自带的 JBR（支持 Java 25 / 17+）执行 Gradle 任务：
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
  # 或自定义安装路径（例如 D:\Program Files\Android\Android Studio\jbr）
  ```

---

## 2. 核心构建与测试命令 (Build & Test)

```powershell
# 1. 运行所有单元测试（全库回归）
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test --console=plain

# 2. 运行单模块单元测试
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :feature:settings:testDebugUnitTest --console=plain

# 3. 仅编译 Kotlin 代码（快速语法与类型检查）
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat compileDebugKotlin --console=plain

# 4. 单模块快速编译验证
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :harness:compileDebugKotlin --console=plain -q

# 5. 编译并打包 Debug APK
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug --console=plain

# 6. 验证架构依赖边界（规则唯一事实源：根目录 architecture-policy.json）
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat architectureCheck --console=plain
# 检查内容：模块依赖白名单（含反向依赖）、依赖图无环、import 黑名单（模型层纯 Kotlin / DAO 直连）、
#          文件尺寸棘轮（maxFileLines=400，存量超限文件登记在 .architecture-baseline.json，只许缩减）

# 6.1 同步尺寸棘轮基线（收缩下调 / 拒绝上涨 / 清理失效条目；代码缩减后运行以下调基线）
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat architectureBaselineSync --console=plain
# 注意：新增模块或调整模块依赖时必须同步登记 architecture-policy.json 的 requires，否则门禁失败

# 7. 仅验证 Harness 内置工具契约与执行策略
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :harness:testDebugUnitTest --tests "top.wkbin.tianxuan.harness.*" --console=plain
```

---

## 3. 设备部署与实时日志调试 (Deploy & Debug)

```powershell
# 1. 安装 Debug APK 到已连接的真机或模拟器
adb install -r app/build/outputs/apk/debug/tianxuan-v0.5.0-debug.apk

# 2. 启动天玄主入口 Activity
adb shell am start -n top.wkbin.tianxuan/.MainActivity

# 3. 实时过滤天玄运行时与智能体核心日志
adb logcat -s TianXuan:V HarnessLoop:V ProotProcess:V
```

## 4. Koin 依赖图回归

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "top.wkbin.tianxuan.di.*" --console=plain
```

修改依赖注册后同时运行完整单元测试和 `:app:assembleDebug`。依赖注入已迁移为 Koin DSL。
