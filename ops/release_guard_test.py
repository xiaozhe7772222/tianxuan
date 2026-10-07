"""release.sh 产物洁净度守卫的端到端自测。

背景：v0.21.0 与 v0.21.1 两个内测包都实测带了 LeakCanary，桌面因此多出一个
「Leaks」小鸟图标应用，且它在进程启动阶段自动初始化，是「打开即闪退」的
高概率来源。release.sh 因此加了「产物洁净度」检查（查 APK 的 manifest 与 dex，
而不是查配置文件——配置会被人改错，产物不会说谎）。

但这个检查本身也可能失效：写错了匹配模式、加正则时转义有误、或者被后续重构
悄悄删掉，它都不会报错，只会在发布时放行一个坏包。这里用「真造 APK」的方式
端到端验证：

  1. 造一个**不含** LeakCanary 的 APK → 守卫必须放行；
  2. 造一个 manifest 里含 LeakLauncherActivity 的 APK → 守卫必须拦下；
  3. 造一个 dex 里含 LeakCanary 类的 APK → 守卫必须拦下。

第 1 条同样重要：守卫若「一律拦下」，发布链路直接不可用，等于没有守卫。

关于「真造 APK」：manifest 分支此前一直没被覆盖，因为手写二进制 AXML 太脆，
当时的注释直接写了「真实包无法在单测内造」并 `del` 掉用例。但那条路是走得通的
——aapt2 link 能从一份纯文本 AndroidManifest.xml 编出**真·二进制 AXML**，
放进 zip 就是一个 aapt2 dump xmltree 能读的真包。造出来的包与实机包在守卫
关心的那两个字段上完全同构，于是 manifest 分支可以被真正跑到，而不是只断言
「release.sh 里还写着那个词」。

运行：python3 release_guard_test.py
"""

import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RELEASE_SH = os.path.join(REPO, "release.sh")

# 与 release.sh 同序回退：显式路径优先，找不到再按版本号排序取最高的一个。
AAPT2_CANDIDATES = [
    "/opt/android-sdk/build-tools/37.0.0/aapt2",
    "/opt/android-sdk/build-tools/36.0.0/aapt2",
]
ANDROID_HOME = os.environ.get("ANDROID_HOME", "/opt/android-sdk")

# 编 AXML 需要 android.jar（aapt2 link 的 -I 参数），取自任一已装 platform。
ANDROID_JAR_CANDIDATES = [
    os.path.join(ANDROID_HOME, "platforms/android-37.1/android.jar"),
    os.path.join(ANDROID_HOME, "platforms/android-36/android.jar"),
    os.path.join(ANDROID_HOME, "platforms/android-35/android.jar"),
]

# release.sh 里那段守卫的等价实现。测试不能直接跑 release.sh 本身——
# 它会真的构建、推送、创建 Release。守卫的判定逻辑必须与 release.sh 中的
# 写法一致，故此处刻意复制同一段匹配模式；若 release.sh 改了模式，
# 这里应同步（另有一条守卫断言 release.sh 中仍保留这些模式，见
# assert_release_sh_still_has_guard）。
#
# 注意这里是**子串**判定，与 release.sh 的 grep -oE 'leakcanary\.[A-Za-z.]*|
# LeakLauncherActivity|…' 在效果上一致：那几个具名分支都是完整类名的一段，
# 只要出现就命中；leakcanary. 开头的分支覆盖了其余组件。
MANIFEST_MARKERS = [
    "leakcanary.internal.activity.LeakActivity",
    "leakcanary.internal.activity.LeakLauncherActivity",
    "leakcanary.internal.PlumberInstaller",
    "leakcanary.internal.MainProcessAppWatcherInstaller",
    "leakcanary.internal.LeakCanaryFileProvider",
]

DEX_MARKER = "Lcom/squareup/leakcanary/AppWatcher;"


# ---- 环境探测 ----------------------------------------------------------------


def find_aapt2():
    for path in AAPT2_CANDIDATES:
        if os.path.exists(path):
            return path
    # 与 release.sh 一致：显式路径都没有时，按版本号排序取最高的一个。
    base = os.path.join(ANDROID_HOME, "build-tools")
    if os.path.isdir(base):
        found = []
        for name in os.listdir(base):
            cand = os.path.join(base, name, "aapt2")
            if os.path.exists(cand):
                found.append((name, cand))

        def ver_key(item):
            return [int(x) for x in re.findall(r"\d+", item[0])] or [0]

        if found:
            return sorted(found, key=ver_key)[-1][1]
    return None


def find_android_jar():
    for path in ANDROID_JAR_CANDIDATES:
        if os.path.exists(path):
            return path
    base = os.path.join(ANDROID_HOME, "platforms")
    if os.path.isdir(base):
        for name in sorted(os.listdir(base), reverse=True):
            cand = os.path.join(base, name, "android.jar")
            if os.path.exists(cand):
                return cand
    return None


# ---- 造真包 ------------------------------------------------------------------


def build_apk(path, aapt2=None, android_jar=None, leak_components=False,
              dex_payload=b""):
    """造一个 aapt2 能解析的真 APK。

    路径一（可行性成立时）：用 aapt2 link 把纯文本 manifest 编成真·二进制
    AXML。这是唯一能让 manifest 分支被真正执行的办法。
    路径二（无 SDK 时的兜底）：写一个占位 manifest，manifest 分支必然不命中，
    此时调用方应把 manifest 相关用例判为 SKIP，而不是当成通过。

    返回 True 表示 manifest 是真·二进制 AXML（manifest 分支可信），
    False 表示走了兜底（manifest 分支未被覆盖）。
    """
    if aapt2 and android_jar:
        src_dir = tempfile.mkdtemp(prefix="rg-manifest-")
        try:
            manifest = os.path.join(src_dir, "AndroidManifest.xml")
            with open(manifest, "w", encoding="utf-8") as f:
                f.write(manifest_source(leak_components))
            r = subprocess.run(
                [aapt2, "link", "-o", path, "--manifest", manifest,
                 "-I", android_jar,
                 "--min-sdk-version", "29", "--target-sdk-version", "37"],
                capture_output=True, text=True,
            )
            if r.returncode != 0:
                raise RuntimeError(f"aapt2 link 失败: {r.stderr.strip()[:300]}")
            real = True
        finally:
            shutil.rmtree(src_dir, ignore_errors=True)

        if dex_payload:
            # aapt2 link 出的包里已有 AndroidManifest.xml，重开 zip 追加 dex。
            with zipfile.ZipFile(path, "a") as zf:
                zf.writestr("classes.dex", dex_payload)
        return real

    manifest = b"\x00" * 8
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("AndroidManifest.xml", manifest)
        if dex_payload:
            zf.writestr("classes.dex", dex_payload)
        zf.writestr("resources.arsc", b"\x00" * 4)
    return False


def manifest_source(leak_components):
    """纯文本 AndroidManifest.xml。

    leak_components=True 时刻意**复刻真实事故包的形态**：LeakLauncherActivity
    带 LAUNCHER intent-filter（桌面多出小鸟图标就是因为这条），
    PlumberInstaller 与 MainProcessAppWatcherInstaller 作为 ContentProvider
    在 attachBaseContext 阶段自动初始化。三者缺一，造出的包就离真实事故更远，
    守卫「能不能认出真包」这个问题的说服力就越弱。
    """
    leak = ""
    if leak_components:
        leak = """
        <activity android:name="leakcanary.internal.activity.LeakLauncherActivity"
                  android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <provider android:name="leakcanary.internal.PlumberInstaller"
                  android:authorities="top.wkbin.tianxuan.leakcanary"
                  android:exported="false" />
        <provider android:name="leakcanary.internal.MainProcessAppWatcherInstaller"
                  android:authorities="top.wkbin.tianxuan.leakcanary.main"
                  android:exported="false" />
        <provider android:name="leakcanary.internal.LeakCanaryFileProvider"
                  android:authorities="top.wkbin.tianxuan.leakcanary.fileprovider"
                  android:exported="false" />
        """
    return f"""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="top.wkbin.tianxuan.debug"
          android:versionCode="33"
          android:versionName="0.21.3-debug">
    <application android:label="天玄">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        {leak}
    </application>
</manifest>
"""


# ---- 守卫的等价实现 ----------------------------------------------------------


def run_guard(apk_path, aapt2):
    """执行 release.sh 中的守卫判定逻辑，返回 (放行?, 说明)。"""
    manifest_xml = subprocess.run(
        [aapt2, "dump", "xmltree", apk_path, "--file", "AndroidManifest.xml"],
        capture_output=True, text=True,
    ).stdout
    hits = sorted({m for m in MANIFEST_MARKERS if m in manifest_xml})
    if hits:
        return False, f"manifest 命中: {hits}"
    tmp = tempfile.mkdtemp(prefix="rg-dex-")
    try:
        subprocess.run(["unzip", "-o", "-q", apk_path, "classes*.dex", "-d", tmp],
                       capture_output=True)
        for name in sorted(os.listdir(tmp)):
            dex = os.path.join(tmp, name)
            if os.path.isfile(dex) and DEX_MARKER.encode() in open(dex, "rb").read():
                return False, f"dex 命中: {name}"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return True, "洁净"


# ---- 反向断言：release.sh 原件里守卫还在 -------------------------------------


def active_lines(script):
    """剥掉注释行，只留 shell 真正会执行的行。

    整段守卫最省事的停用办法就是在关键行前加个 #，而那一行的模式串照样躺在
    文件里。若拿全文去找「第一个 grep -oE」，被注释掉的那行也会被当成
    「守卫还在」——本自测就又退化成一个子串断言了。
    """
    return "\n".join(
        ln for ln in script.splitlines()
        if not ln.lstrip().startswith("#")
    )


def extract_manifest_grep_pattern(script):
    """从 release.sh 里抠出 manifest 守卫实际用的那个 grep -oE 模式。

    为什么要抠模式、而不是搜关键词：`grep -n LeakLauncherActivity release.sh`
    会**先命中注释**（第 152 行注释里就写着这个词）。把模式里那一处拼错成
    LeakLauncherActvty 之后，注释和报错文案里的正确拼写仍在，纯子串断言照样通过
    ——这正是上一版自测的漏洞：它测的是「文档里提过这个词」，不是「守卫真在查它」。
    所以必须取真正会被 grep 执行的那一行，且必须在未注释的行上取。
    """
    # 守卫里唯一的 grep -oE 就在拼 LEAK_COMPONENTS 的那个命令替换里。
    m = re.search(r"""grep\s+-oE\s+'([^']+)'""", active_lines(script))
    if m is None:
        raise AssertionError(
            "release.sh 里找不到产物守卫的 grep -oE 模式（未被注释的）——"
            "要么守卫被删/被注释掉了，要么写法变了，请同步本自测")
    # 取到的是 shell **单引号内**的字面文本，即 grep 实际收到的那个正则串。
    # 源文件里写的是 'leakcanary\.[A-Za-z.]*'（一个反斜杠），grep -E 里 \. 表示
    # 转义的「字面点」，与未转义 . 在**本模式中的用途**上等价（该分支只想匹配
    # "leakcanary." 这个字面前缀，后面才用 [A-Za-z.]*）。
    # 逐分支比较前先把正则转义还原成字面量，否则 'leakcanary\.' 会因多一个反斜杠
    # 匹配不上 'leakcanary.'，把「缺分支」误报成真。
    pattern_literal = re.sub(r"\\(.)", r"\1", m.group(1))
    return pattern_literal


def assert_release_sh_still_has_guard():
    """反向断言：release.sh 里这些标记不能被删掉，且必须还在**生效的位置**上。

    守卫逻辑复制在本文件中，若 release.sh 那侧被重构删掉，本文件的自测照样绿——
    这是「测的是复制品、跑的是原件」的经典陷阱。这里直接断言原件里还在。

    拆成两层：
      * 结构断言（关键）：从 release.sh 抠出真正被 grep 执行的模式，
        再要求模式里每个分支都完整、且这些具名类都在模式里。这条能挡住
        「把模式拼错 / 删掉某个分支 / 整段守卫被注释掉」这三类退化。
      * 文案断言（辅助）：守卫的提示文案仍在，用于发现整段被删。
        文案断言不能单独作为验收依据，原因见 extract_manifest_grep_pattern。
    """
    script = open(RELEASE_SH, encoding="utf-8").read()

    # 守卫必须真的在跑：提示文案与被拦分支都在。
    prose = ["校验产物洁净度", "tianxuan.leakcanary"]
    missing = [m for m in prose if m not in script]
    if missing:
        raise AssertionError(f"release.sh 的产物守卫缺少关键标记: {missing}")

    # 关键：抠出 grep -oE 的**模式本体**，逐分支核对。
    pattern = extract_manifest_grep_pattern(script)
    alternatives = [a for a in pattern.split("|") if a.strip()]
    if not alternatives:
        raise AssertionError(f"release.sh 的 manifest 守卫模式为空: {pattern!r}")

    # 具名类必须各自成为模式里的一个活分支；拼错一个字都会在这里暴露。
    required_live = [
        "LeakLauncherActivity",
        "LeakActivity",
        "PlumberInstaller",
        "MainProcessAppWatcherInstaller",
    ]
    missing_live = [c for c in required_live if c not in alternatives]
    if missing_live:
        raise AssertionError(
            "release.sh 的 manifest 守卫模式里缺少这些分支（被删或拼错？）: "
            f"{missing_live}；当前模式 {pattern!r}")

    # 兜底分支必须覆盖 leakcanary. 前缀，否则将来新增组件会漏。
    if not any(a.startswith("leakcanary.") for a in alternatives):
        raise AssertionError(
            f"release.sh 的 manifest 守卫模式缺少 'leakcanary.' 前缀兜底分支：{pattern!r}")

    # dex 守卫：同样只在**未注释的行**里找，理由与 manifest 模式一致。
    dex_line = [ln for ln in active_lines(script).splitlines()
                if "Lcom/squareup/leakcanary/" in ln and "strings" in ln
                and "grep" in ln]
    if not dex_line:
        raise AssertionError(
            "release.sh 里找不到 dex 的 LeakCanary 守卫行（未被注释的）"
            "——被删或被注释掉了？")


# ---- 用例 --------------------------------------------------------------------


def main():
    try:
        assert_release_sh_still_has_guard()
    except AssertionError as e:
        print(f"FAIL: {e}", file=sys.stderr)
        return 1

    aapt2 = find_aapt2()
    android_jar = find_android_jar()
    failures = []
    skipped = []

    if aapt2 is None:
        print("SKIP: 找不到 aapt2，无法执行产物守卫自测", file=sys.stderr)
        return 0

    tmp = tempfile.mkdtemp(prefix="rg-test-")
    try:
        # 1) 干净 APK 必须放行。守卫若「一律拦下」，发布链路直接不可用。
        clean = os.path.join(tmp, "clean.apk")
        real_manifest = build_apk(clean, aapt2, android_jar, leak_components=False)
        allowed, why = run_guard(clean, aapt2)
        if allowed is not True:
            failures.append(f"干净 APK 被误拦: {why}")

        # 2) manifest 含 LeakCanary 组件必须拦下（本次修复的核心用例）。
        if real_manifest:
            dirty_manifest = os.path.join(tmp, "dirty_manifest.apk")
            build_apk(dirty_manifest, aapt2, android_jar, leak_components=True)
            allowed, why = run_guard(dirty_manifest, aapt2)
            if allowed is not False:
                failures.append(
                    "manifest 含 LeakCanary 组件的 APK 未被拦下"
                    f"（放行={allowed}，{why}）")
            elif "manifest" not in why:
                failures.append(
                    f"含组件的 APK 被拦下了，但不是 manifest 分支判的: {why}")
        else:
            skipped.append("manifest 分支：无 android.jar，无法编真 AXML")

        # 3) dex 含 LeakCanary 类必须拦下。
        dirty_dex = os.path.join(tmp, "dirty_dex.apk")
        build_apk(dirty_dex, aapt2, android_jar, leak_components=False,
                  dex_payload=b"payload\x00" + DEX_MARKER.encode() + b"\x00" * 32)
        allowed, why = run_guard(dirty_dex, aapt2)
        if allowed is not False:
            failures.append(f"含 LeakCanary 类的 APK 未被拦下（放行={allowed}，{why}）")

        # 4) 干净 APK 不能含该类，否则用例 3 无意义（负对照）。
        allowed, why = run_guard(clean, aapt2)
        if allowed is not True:
            failures.append(f"干净 APK 未放行: {why}")

        # 5) 只改包名的对照：守卫查的是组件，不是包名。
        #    这条防的是「守卫被写成查包名 top.wkbin.tianxuan.leakcanary」这种
        #    看似合理、实则漏掉真组件的写法。
        #    造包时 authorities 已带 .leakcanary，若守卫误按包名判定，
        #    干净包也会被拦——用例 1 已覆盖；此处再确认组件判定与包名无关。
        if real_manifest:
            for m in MANIFEST_MARKERS:
                if m in _dump(clean, aapt2):
                    failures.append(f"干净 APK 的 manifest 里出现了 {m}")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    for s in skipped:
        print(f"SKIP: {s}", file=sys.stderr)
    if failures:
        for f in failures:
            print(f"FAIL: {f}", file=sys.stderr)
        return 1
    print("release_guard_test: OK（干净包放行、manifest 含组件的包被拦、"
          "dex 含类的包被拦、release.sh 守卫模式逐分支核对通过）")
    return 0


def _dump(apk, aapt2):
    return subprocess.run(
        [aapt2, "dump", "xmltree", apk, "--file", "AndroidManifest.xml"],
        capture_output=True, text=True,
    ).stdout


if __name__ == "__main__":
    sys.exit(main())
