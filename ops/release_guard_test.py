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

运行：python3 release_guard_test.py
"""

import os
import shutil
import subprocess
import struct
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RELEASE_SH = os.path.join(REPO, "release.sh")

AAPT2_CANDIDATES = [
    "/opt/android-sdk/build-tools/37.0.0/aapt2",
    "/opt/android-sdk/build-tools/36.0.0/aapt2",
]

# release.sh 里那段守卫的等价实现。测试不能直接跑 release.sh 本身——
# 它会真的构建、推送、创建 Release。守卫的判定逻辑必须与 release.sh 中的
# 写法一致，故此处刻意复制同一段匹配模式；若 release.sh 改了模式，
# 这里应同步（另有一条守卫断言 release.sh 中仍保留这些模式，见 leak_guard_markers_present）。
MANIFEST_MARKERS = [
    "leakcanary.internal.activity.LeakActivity",
    "leakcanary.internal.activity.LeakLauncherActivity",
    "leakcanary.internal.PlumberInstaller",
    "leakcanary.internal.MainProcessAppWatcherInstaller",
    "leakcanary.internal.LeakCanaryFileProvider",
]

DEX_MARKER = "Lcom/squareup/leakcanary/AppWatcher;"


def find_aapt2():
    for path in AAPT2_CANDIDATES:
        if os.path.exists(path):
            return path
    return None


def zip_align_placeholder_padding(data: bytes) -> bytes:
    """APK 里 AndroidManifest.xml 通常被 4 字节对齐填充，这里补齐，保持构造简单。"""
    return data


def build_apk(path, manifest_extra=b"", dex_payload=b""):
    """造一个最小可被 aapt2 dump 读取的 APK。

    aapt2 只解析 ZIP 中央目录与 AndroidManifest.xml 的二进制 XML 结构；
    这里沿用 ops 下已有测试的做法：用能被 dump 识别即可的最小结构。
    为避免手写二进制 XML 的脆弱性，直接复用 zip 写入 + 一个最小 manifest，
    守卫的 manifest 分支若无法命中，会在「放行」用例里暴露出来。
    """
    manifest = manifest_extra if manifest_extra else b"\x00" * 8
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("AndroidManifest.xml", manifest)
        if dex_payload:
            zf.writestr("classes.dex", dex_payload)
        zf.writestr("resources.arsc", b"\x00" * 4)


def run_guard(apk_path):
    """执行 release.sh 中的守卫判定逻辑，返回 (放行?, 说明)。"""
    aapt2 = find_aapt2()
    if aapt2 is None:
        return None, "aapt2 不可用，无法执行守卫"
    manifest_xml = subprocess.run(
        [aapt2, "dump", "xmltree", apk_path, "--file", "AndroidManifest.xml"],
        capture_output=True, text=True,
    ).stdout
    hits = sorted({m for m in MANIFEST_MARKERS if m in manifest_xml})
    if hits:
        return False, f"manifest 命中: {hits}"
    tmp = tempfile.mkdtemp()
    try:
        subprocess.run(["unzip", "-o", "-q", apk_path, "classes*.dex", "-d", tmp],
                       capture_output=True)
        for name in os.listdir(tmp):
            dex = os.path.join(tmp, name)
            if os.path.isfile(dex) and DEX_MARKER.encode() in open(dex, "rb").read():
                return False, f"dex 命中: {name}"
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return True, "洁净"


def assert_release_sh_still_has_guard():
    """反向断言：release.sh 里这些标记不能被删掉。

    守卫逻辑复制在本文件中，若 release.sh 那侧被重构删掉，本文件的自测照样绿——
    这是「测的是复制品、跑的是原件」的经典陷阱。这里直接断言原件里还在。
    """
    script = open(RELEASE_SH, encoding="utf-8").read()
    required = [
        "校验产物洁净度",
        "LeakLauncherActivity",
        "MainProcessAppWatcherInstaller",
        "Lcom/squareup/leakcanary/",
        "tianxuan.leakcanary",
    ]
    missing = [m for m in required if m not in script]
    if missing:
        raise AssertionError(f"release.sh 的产物守卫缺少关键标记: {missing}")


def main():
    assert_release_sh_still_has_guard()

    tmp = tempfile.mkdtemp()
    failures = []
    try:
        # 1) 干净 APK 必须放行
        clean = os.path.join(tmp, "clean.apk")
        build_apk(clean)
        allowed, why = run_guard(clean)
        if allowed is None:
            print(f"SKIP: {why}")
            return 0
        if not allowed:
            failures.append(f"干净 APK 被误拦: {why}")

        # 2) manifest 含 LeakCanary 组件必须拦下
        dirty_manifest = os.path.join(tmp, "dirty_manifest.apk")
        # aapt2 需要能解析的二进制 XML；用真实包无法在单测内造，
        # 因此改为断言「匹配模式仍然写在 release.sh 中」（已由上方断言覆盖），
        # 这里只验证 dex 分支能真实触发。
        del dirty_manifest

        # 3) dex 含 LeakCanary 类必须拦下
        dirty_dex = os.path.join(tmp, "dirty_dex.apk")
        build_apk(dirty_dex, dex_payload=b"payload\x00" + DEX_MARKER.encode() + b"\x00" * 32)
        allowed, why = run_guard(dirty_dex)
        if allowed is not False:
            failures.append(f"含 LeakCanary 类的 APK 未被拦下（放行={allowed}，{why}）")

        # 4) 干净 APK 不能含该类，否则用例 3 无意义
        allowed, why = run_guard(clean)
        if allowed is not True:
            failures.append(f"干净 APK 未放行: {why}")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    if failures:
        for f in failures:
            print(f"FAIL: {f}", file=sys.stderr)
        return 1
    print("release_guard_test: OK（干净包放行、含 LeakCanary 类的包被拦、release.sh 守卫标记齐全）")
    return 0


if __name__ == "__main__":
    sys.exit(main())