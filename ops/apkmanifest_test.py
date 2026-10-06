"""apkmanifest 的自测：用构造的最小 AXML 覆盖关键分支。

不依赖真实 APK，可在无 Android SDK 的机器上跑。
运行：python3 apkmanifest_test.py
"""

import importlib.util
import os
import struct
import sys
import tempfile
import zipfile

spec = importlib.util.spec_from_file_location(
    "apkmanifest", os.path.join(os.path.dirname(os.path.abspath(__file__)), "apkmanifest.py"))
am = importlib.util.module_from_spec(spec)
spec.loader.exec_module(am)


def build_pool_chunk(strings, utf8=False):
    """构造完整的字符串池 chunk（含 8 字节 chunk 头）。"""
    offsets = []
    blob = bytearray()
    for s in strings:
        offsets.append(len(blob))
        if utf8:
            b = s.encode("utf-8")
            blob += bytes([len(s)]) + bytes([len(b)]) + b + b"\x00"
        else:
            u = s.encode("utf-16-le")
            blob += struct.pack("<H", len(s)) + u + b"\x00\x00"
    strings_start = 28 + 4 * len(strings)
    size = strings_start + len(blob)
    head = struct.pack("<HHI", 0x0001, 28, size)      # type/headerSize/size
    head += struct.pack("<I", len(strings))          # stringCount
    head += struct.pack("<I", 0)                     # styleCount
    head += struct.pack("<I", (1 << 8) if utf8 else 0)  # flags
    head += struct.pack("<I", strings_start)         # stringsStart
    head += struct.pack("<I", 0)                     # stylesStart
    for o in offsets:
        head += struct.pack("<I", o)
    return head + bytes(blob)


def build_start_element(name_idx, attrs):
    """构造 START_ELEMENT chunk。attrs = [(name_idx, raw_idx, type, data)]

    与真实 AXML 一致：attributeStart 取 20（即 attrExt 长度），相对 attrExt 起点。
    """
    header_size = 16
    # attrExt: ns(4) name(4) + attributeStart/Size/Count(2*3) + id/class/style(2*3) = 20
    attr_ext = struct.pack("<I", 0xFFFFFFFF) + struct.pack("<I", name_idx)
    attr_ext += struct.pack("<HHH", 20, 20, len(attrs))
    attr_ext += struct.pack("<HHH", 0, 0, 0)  # id/class/style
    body = b""
    for a_name, a_raw, a_type, a_data in attrs:
        body += struct.pack("<IIIHBBI", 0xFFFFFFFF, a_name, a_raw, 8, 0, a_type, a_data)
    # chunk 大小 = chunk头(8) + lineNumber(4) + comment(4) + attrExt + 属性体
    size = 8 + 8 + len(attr_ext) + len(body)
    return struct.pack("<HHI", 0x0102, header_size, size) + \
        struct.pack("<I", 2) + struct.pack("<I", 0xFFFFFFFF) + attr_ext + body


def build_axml(strings, utf8=False, attrs=None, name_idx=0, add_resource_map=True):
    head = struct.pack("<HHI", 0x0003, 8, 8)
    pool = build_pool_chunk(strings, utf8)
    # 真实 APK 里字符串池与首个元素之间还有一个 resource map chunk
    if add_resource_map:
        n = 4
        pool += struct.pack("<HHI", 0x0180, 8, 8 + 4 * n) + b"\x00" * (4 * n)
    if attrs is None:
        attrs = []
    return head + pool + build_start_element(name_idx, attrs)


def make_apk(manifest_bytes):
    fd, path = tempfile.mkstemp(suffix=".apk")
    os.close(fd)
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("AndroidManifest.xml", manifest_bytes)
    return path


CASES = []


def case(fn):
    CASES.append(fn)
    return fn


@case
def test_utf16_namespace_prefix():
    """真实 APK 的形态：UTF-16 池 + android: 前缀属性。"""
    strings = ["manifest", "versionCode", "versionName",
               "package", "0.20.0-debug", "top.wkbin.tianxuan.debug"]
    attrs = [(1, 1, 0x10, 29), (2, 4, 0x03, 4), (3, 5, 0x03, 5)]
    p = make_apk(build_axml(strings, utf8=False, attrs=attrs))
    a = am.read_manifest_attrs(p)
    assert a.get("versionCode") == "29", a
    assert a.get("versionName") == "0.20.0-debug", a
    assert a.get("package") == "top.wkbin.tianxuan.debug", a
    os.unlink(p)


@case
def test_utf8_pool():
    """UTF-8 池也要能解。"""
    strings = ["manifest", "versionCode", "package", "com.x.y"]
    attrs = [(1, 1, 0x10, 7), (2, 3, 0x03, 3)]
    p = make_apk(build_axml(strings, utf8=True, attrs=attrs))
    a = am.read_manifest_attrs(p)
    assert a.get("versionCode") == "7", a
    assert a.get("package") == "com.x.y", a
    os.unlink(p)


@case
def test_skips_non_manifest_root():
    """根元素不是 manifest 时返回空，不误取子元素属性。"""
    strings = ["application", "label", "天玄"]
    attrs = [(1, 2, 0x03, 2)]
    p = make_apk(build_axml(strings, utf8=False, attrs=attrs))
    assert am.read_manifest_attrs(p) == {}
    os.unlink(p)


@case
def test_integer_types():
    """INT_DEC / INT_BOOLEAN 都应转成可读字符串。"""
    strings = ["manifest", "flag", "num"]
    attrs = [(1, 1, 0x12, 1), (2, 2, 0x10, 300)]
    p = make_apk(build_axml(strings, utf8=False, attrs=attrs))
    a = am.read_manifest_attrs(p)
    assert a.get("flag") == "true", a
    assert a.get("num") == "300", a
    os.unlink(p)


@case
def test_apk_identity_tuple():
    strings = ["manifest", "versionCode", "package", "com.x.y"]
    attrs = [(1, 1, 0x10, 29), (2, 3, 0x03, 3)]
    p = make_apk(build_axml(strings, utf8=False, attrs=attrs))
    pkg, vn, vc = am.apk_identity(p)
    assert (pkg, vn, vc) == ("com.x.y", "", "29"), (pkg, vn, vc)
    os.unlink(p)


@case
def test_bad_inputs_do_not_raise():
    """非 APK / 不存在 / 损坏文件都只返回空，不抛异常。"""
    fd, p = tempfile.mkstemp(suffix=".apk")
    os.write(fd, b"not a zip at all")
    os.close(fd)
    assert am.read_manifest_attrs(p) == {}
    assert am.read_manifest_attrs("/nonexistent.apk") == {}
    # 空文件
    fd2, p2 = tempfile.mkstemp(suffix=".apk")
    os.close(fd2)
    assert am.read_manifest_attrs(p2) == {}
    for x in (p, p2):
        os.unlink(x)


@case
def test_zip_without_manifest():
    """合法 zip 但没有 AndroidManifest.xml。"""
    fd, p = tempfile.mkstemp(suffix=".apk")
    os.close(fd)
    with zipfile.ZipFile(p, "w") as z:
        z.writestr("classes.dex", b"x")
    assert am.read_manifest_attrs(p) == {}
    os.unlink(p)


if __name__ == "__main__":
    failed = 0
    for fn in CASES:
        try:
            fn()
            print("  PASS  %s" % fn.__name__)
        except Exception as e:
            failed += 1
            print("  FAIL  %s: %r" % (fn.__name__, e))
    print("--- %d/%d 通过" % (len(CASES) - failed, len(CASES)))
    sys.exit(1 if failed else 0)