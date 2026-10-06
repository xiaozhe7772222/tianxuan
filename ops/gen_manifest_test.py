"""gen-manifest.sh 的端到端自测。

这个脚本会把「哪个版本是 latest」和「首页展示哪个包名」交给
排序与循环内的赋值决定，出错时不会抛异常，只会让用户拿到旧包
或看到对不上的包名——所以必须真跑一遍验证，而不是靠读代码。

做法：构造若干个「假 APK」（只需能被 apkmanifest 读出清单），
真实执行 gen-manifest.sh，再断言 manifest.json 的内容。

运行：python3 gen_manifest_test.py
"""

import importlib.util
import json
import os
import shutil
import struct
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
# 被测脚本的位置。开发态在同目录（ops/gen-manifest.sh），
# 部署到服务器后装的是 /usr/local/bin/gen-tianxuan-manifest.sh，
# 故允许用 TIANXUAN_GEN_MANIFEST 覆盖——两处不该各写一份路径，
# 写死了服务器上的测试就会误报「找不到脚本」，像是脚本坏了。
GEN = os.environ.get("TIANXUAN_GEN_MANIFEST",
                     os.path.join(HERE, "gen-manifest.sh"))

spec = importlib.util.spec_from_file_location(
    "apkmanifest", os.path.join(HERE, "apkmanifest.py"))
am = importlib.util.module_from_spec(spec)
spec.loader.exec_module(am)


# ---- 构造最小可解析 AXML（沿用 apkmanifest_test 的构造逻辑） ----------------

# 构造可被 apkmanifest 解析的 AXML。
# 这里刻意复用 apkmanifest_test 里那份已验证的实现，而不是自己再写一份：
# 之前自己拼的版本把 START_ELEMENT 头写成 8 字节（真实 AXML 是 16 字节，
# 还含 lineNumber/comment），结果解析整棵树失败、属性全空，排查绕了很久。
# 复用同一构造器，解析器与被测数据的一致性才不会各写各的。
_AXML = importlib.util.spec_from_file_location(
    "apkmanifest_test", os.path.join(HERE, "apkmanifest_test.py"))
_axml_mod = importlib.util.module_from_spec(_AXML)
_AXML.loader.exec_module(_axml_mod)


def build_manifest_xml(package, version_name, version_code):
    """返回 AndroidManifest.xml 的字节内容。

    池布局：0=manifest 1=versionCode 2=versionName 3=package 4=版本名 5=包名
    versionCode 是整型属性（type 0x10），值不进池；package/versionName 是字符串属性。
    """
    version_code = int(version_code)
    strings = ["manifest", "versionCode", "versionName",
               "package", version_name, package]
    attrs = [
        (1, 1, 0x10, version_code),  # versionCode
        (2, 2, 0x03, 4),             # versionName -> 池下标 4
        (3, 3, 0x03, 5),             # package   -> 池下标 5
    ]
    return _axml_mod.build_axml(strings, utf8=False, attrs=attrs, name_idx=0)


def make_apk(path, package, version_name, version_code):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("AndroidManifest.xml",
                   build_manifest_xml(package, version_name, version_code))
        z.writestr("classes.dex", b"")   # 让 zip 像个真包
        z.writestr("payload.bin", os.urandom(512))


# ---- 断言小工具 --------------------------------------------------------------

FAILURES = []


def check(label, condition, detail=""):
    if condition:
        print("  PASS  %s" % label)
    else:
        print("  FAIL  %s %s" % (label, detail))
        FAILURES.append(label)


def run_case(name, entries, expect_latest, expect_app_package,
             expect_order=None):
    """entries: [(目录名, 包名, versionName, versionCode)]"""
    tmp = tempfile.mkdtemp(prefix="gm-test-")
    try:
        dist = os.path.join(tmp, "dist")
        os.makedirs(dist)
        for dirname, pkg, vn, vc in entries:
            d = os.path.join(dist, dirname)
            os.makedirs(d)
            make_apk(os.path.join(d, "tianxuan-v%s-release.apk" % vn), pkg, vn, vc)

        manifest = os.path.join(dist, "manifest.json")
        # apkmanifest.py 必须与被测脚本同处一份（脚本内按 TIANXUAN_APKMANIFEST
        # 找它，默认为 /usr/local/share/tianxuan/）。这里显式指到本仓库那份，
        # 保证测的是当前代码，而不是服务器上可能更旧的副本。
        apkmod = os.path.join(HERE, "apkmanifest.py")

        import subprocess
        env = dict(os.environ, TIANXUAN_DIST=dist, TIANXUAN_APKMANIFEST=apkmod)
        r = subprocess.run(["bash", GEN], capture_output=True, text=True, env=env)
        if r.returncode != 0:
            check("%s: 脚本退出码 0" % name, False, r.stderr.strip()[:200])
            return
        if not os.path.exists(manifest):
            check("%s: 生成 manifest.json" % name, False)
            return

        data = json.load(open(manifest, encoding="utf-8"))
        check("%s: latest=%s" % (name, expect_latest),
              data.get("latest") == expect_latest,
              "实际 %s" % data.get("latest"))
        check("%s: app.package=%s" % (name, expect_app_package),
              data.get("app", {}).get("package") == expect_app_package,
              "实际 %s" % data.get("app", {}).get("package"))
        if expect_order is not None:
            actual = [v["version"] for v in data["versions"]]
            check("%s: 排序 %s" % (name, expect_order), actual == expect_order,
                  "实际 %s" % actual)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def test_latest_follows_version_code():
    """versionCode 更高的版本必须成为 latest，哪怕目录名字母序在后。"""
    run_case(
        "latest 取 versionCode 最高",
        # 目录名 0.9.0 字母序在 0.21.0 之后/之前都不该影响结果
        [("0.9.0", "top.wkbin.tianxuan", "0.9.0", "9"),
         ("0.21.0", "top.wkbin.tianxuan", "0.21.0", "30")],
        expect_latest="v0.21.0",
        expect_app_package="top.wkbin.tianxuan",
        expect_order=["0.21.0", "0.9.0"],
    )


def test_app_metadata_from_newest():
    """app 元数据取最新版本，不能被字母序最后的旧版本覆盖。"""
    # "0.9.0" 在字母序上小于 "0.21.0"，但若按遍历覆盖则 app 会拿到哪个取决于顺序。
    # 这里刻意让高版本的包名带 .dev 之外的正式名，旧版本用不同包名，
    # 若按「最后遍历到的」取值就会取错。
    run_case(
        "app 元数据取最新而非字母序末位",
        [("0.9.0", "top.wkbin.tianxuan.old", "0.9.0", "9"),
         ("0.21.0", "top.wkbin.tianxuan", "0.21.0", "30")],
        expect_latest="v0.21.0",
        expect_app_package="top.wkbin.tianxuan",
    )


def test_build_suffix_stripped():
    """构建类型后缀不属于版本号：-debug/-release 都要清掉。"""
    run_case(
        "清除构建类型后缀",
        [("0.21.0", "top.wkbin.tianxuan", "0.21.0-release", "30")],
        expect_latest="v0.21.0",
        expect_app_package="top.wkbin.tianxuan",
    )


def test_unparsable_code_sorts_last():
    """读不到 versionCode 的包不能被推成 latest。"""
    tmp = tempfile.mkdtemp(prefix="gm-test-bad-")
    try:
        dist = os.path.join(tmp, "dist")
        os.makedirs(os.path.join(dist, "0.30.0"))
        os.makedirs(os.path.join(dist, "0.21.0"))
        # 故意放一个不是有效 AXML 的 APK：解析会失败，versionCode 留 0
        with zipfile.ZipFile(os.path.join(dist, "0.30.0", "broken.apk"), "w") as z:
            z.writestr("AndroidManifest.xml", b"not-a-xml-at-all")
        make_apk(os.path.join(dist, "0.21.0", "tianxuan-v0.21.0-release.apk"),
                 "top.wkbin.tianxuan", "0.21.0", "30")
        apkmod = os.path.join(HERE, "apkmanifest.py")
        import subprocess
        env = dict(os.environ, TIANXUAN_DIST=dist, TIANXUAN_APKMANIFEST=apkmod)
        r = subprocess.run(["bash", GEN], capture_output=True, text=True, env=env)
        check("坏 APK 不致脚本崩溃", r.returncode == 0, r.stderr.strip()[:200])
        if r.returncode == 0:
            data = json.load(open(os.path.join(dist, "manifest.json"), encoding="utf-8"))
            check("解析失败的版本不作为 latest",
                  data.get("latest") == "v0.21.0", "实际 %s" % data.get("latest"))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def test_idempotent():
    """内容不变时不应改写文件（幂等），否则每次发布都触发无谓的服务读盘。"""
    tmp = tempfile.mkdtemp(prefix="gm-test-idem-")
    try:
        dist = os.path.join(tmp, "dist")
        os.makedirs(os.path.join(dist, "0.21.0"))
        make_apk(os.path.join(dist, "0.21.0", "tianxuan-v0.21.0-release.apk"),
                 "top.wkbin.tianxuan", "0.21.0", "30")
        apkmod = os.path.join(HERE, "apkmanifest.py")
        manifest = os.path.join(dist, "manifest.json")
        import subprocess
        env = dict(os.environ, TIANXUAN_DIST=dist, TIANXUAN_APKMANIFEST=apkmod)
        subprocess.run(["bash", GEN], capture_output=True, text=True, env=env)
        first = os.stat(manifest).st_mtime_ns
        r2 = subprocess.run(["bash", GEN], capture_output=True, text=True, env=env)
        second = os.stat(manifest).st_mtime_ns
        check("重复执行不改写清单", first == second and "无变化" in r2.stdout,
              r2.stdout.strip()[:120])
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    if not os.path.exists(GEN):
        print("找不到 gen-manifest.sh：%s" % GEN)
        sys.exit(1)
    for fn in (test_latest_follows_version_code,
               test_app_metadata_from_newest,
               test_build_suffix_stripped,
               test_unparsable_code_sorts_last,
               test_idempotent):
        print("\n[%s]" % fn.__name__)
        fn()
    print("\n%d 项断言失败" % len(FAILURES) if FAILURES else "\n全部通过")
    sys.exit(1 if FAILURES else 0)