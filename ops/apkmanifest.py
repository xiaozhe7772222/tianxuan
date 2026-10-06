"""从二进制 AXML(AndroidManifest.xml) 读取属性值。

服务器上没有 aapt/apktool，且不该为读一个版本号去装 Android SDK。
这里用标准库 struct + zipfile 直接解析 AXML，够取出版本号、包名即可。

三个容易踩的坑（都已在下方注释标出）：
1. 字符串池的 UTF-8 标志位在 flags 的 bit 8（0x100）。现代 aapt2 生成的
   清单通常是 UTF-16（flags=0x0），按 UTF-8 解会得到乱码并导致后续全部错位。
2. chunk 之间要按 header 里的 size 字段整块跳过，不能只跳 headerSize。
   中间存在 RES_XML_RESOURCE_MAP_TYPE(0x0180) 等 chunk，只跳头会读到错位数据。
3. START_ELEMENT 的属性表在 attrExt 之后，基址要用 headerSize 推：
   chunk头(headerSize) + attrExt(20 字节) + attributeStart。
"""

import struct
import zipfile

CHUNK_STRING_POOL = 0x0001
CHUNK_XML_START_ELEMENT = 0x0102
TYPE_STRING = 0x03
NO_ENTRY = 0xFFFFFFFF
ATTR_EXT_SIZE = 20  # ns(4) name(4) + attributeStart/Size/Count + id/class/style 各 2


def _read_string_pool(data, off):
    """解析字符串池 chunk，返回 (字符串列表, 池后偏移)。"""
    if off + 28 > len(data):
        raise ValueError("字符串池头被截断")
    chunk_size = struct.unpack_from("<I", data, off + 4)[0]
    if chunk_size <= 0 or off + chunk_size > len(data):
        raise ValueError("字符串池 chunk 大小非法")
    str_cnt, _sty_cnt, flags = struct.unpack_from("<III", data, off + 8)
    str_off = struct.unpack_from("<I", data, off + 20)[0]
    if 28 + 4 * str_cnt > chunk_size:
        raise ValueError("字符串数量与chunk 大小不符")
    utf8 = bool(flags & 0x100)
    base = off + str_off
    end = off + chunk_size

    strings = []
    for i in range(str_cnt):
        idx = struct.unpack_from("<I", data, off + 28 + 4 * i)[0]
        p = base + idx
        if p < off or p >= end:
            strings.append("")
            continue
        try:
            if utf8:
                # UTF-8：先「字符数」再「字节数」，各自 1~2 字节变长
                n = data[p]
                p += 1
                if n & 0x80:
                    if p >= end:
                        strings.append("")
                        continue
                    n = ((n & 0x7F) << 8) | data[p]
                    p += 1
                if p >= end:
                    strings.append("")
                    continue
                m = data[p]
                p += 1
                if m & 0x80:
                    if p >= end:
                        strings.append("")
                        continue
                    m = ((m & 0x7F) << 8) | data[p]
                    p += 1
                if p + m > end:
                    strings.append("")
                    continue
                strings.append(data[p:p + m].decode("utf-8", "replace"))
            else:
                if p + 2 > end:
                    strings.append("")
                    continue
                n = struct.unpack_from("<H", data, p)[0]
                p += 2
                if n & 0x8000:
                    if p + 2 > end:
                        strings.append("")
                        continue
                    n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                    p += 2
                if p + n * 2 > end:
                    strings.append("")
                    continue
                strings.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
        except (IndexError, struct.error, UnicodeDecodeError):
            strings.append("")
    return strings, end


def read_manifest_attrs(apk_path):
    """返回 {属性名: 值} —— 取自AndroidManifest.xml 的根 <manifest> 元素。

    属性名统一去掉命名空间前缀：`android:versionCode` -> `versionCode`。
    不能直接按短名比对字符串池索引 —— 平台属性的池内字符串是带前缀的
    完整名（aapt2 dump xmltree 可见 `android:versionCode`），
    只有应用自定义属性（package）才是裸名。

    值统一按字符串返回；非字符串类型（如 versionCode）返回十进制字符串。
    解析失败时返回空字典，不抛异常（调用方自行决定是否报错）。
    """
    try:
        with zipfile.ZipFile(apk_path) as z:
            data = z.read("AndroidManifest.xml")
    except (KeyError, OSError, zipfile.BadZipFile):
        return {}

    # 文件头：type=0x0003, headerSize=8；magic 低16 位为 0x0003
    if len(data) < 16 or struct.unpack_from("<H", data, 0)[0] != 0x0003:
        return {}

    off = 8
    strings = []
    # 先定位字符串池（它是紧跟文件头的第一个 chunk）
    try:
        while off + 8 <= len(data):
            ctype = struct.unpack_from("<H", data, off)[0]
            if ctype == CHUNK_STRING_POOL:
                strings, off = _read_string_pool(data, off)
                break
            csize = struct.unpack_from("<I", data, off + 4)[0]
            if csize <= 0:
                return {}
            off += csize
    except (struct.error, ValueError):
        return {}
    if not strings:
        return {}

    # 逐 chunk 前进（用 size 整块跳过），找到根元素
    while off + 8 <= len(data):
        ctype, header_size, csize = struct.unpack_from("<HHI", data, off)
        # 截断或尺寸非法的 chunk 直接放弃：宁可返回空，也不能让调用方崩
        if csize <= 0 or header_size < 8 or off + csize > len(data):
            return {}
        if ctype == CHUNK_XML_START_ELEMENT:
            ext = off + header_size
            if ext + ATTR_EXT_SIZE > len(data):
                return {}
            name_idx = struct.unpack_from("<I", data, ext + 4)[0]
            a_start, a_size, a_count = struct.unpack_from("<HHH", data, ext + 8)
            # 只认根 <manifest>；子元素（如 application/activity/uses-sdk）跳过
            if name_idx >= len(strings) or strings[name_idx] != "manifest":
                off += csize
                continue
            attrs = {}
            # attributeStart 是相对 attrExt 起点（即 off+header_size）的偏移，
            # 不是相对 attrExt 末尾 —— 不要再叠加 ATTR_EXT_SIZE，否则会跳过首属性。
            base = ext + a_start
            if a_size < 20:
                return {}
            for i in range(a_count):
                pos = base + i * a_size
                if pos + 20 > len(data):
                    break
                _ns, a_name, a_raw, _sz, _res0, a_type, a_data = \
                    struct.unpack_from("<IIIHBBI", data, pos)
                if a_name >= len(strings):
                    continue
                key = strings[a_name]
                if ":" in key:          # 去掉 android: / tools: 之类前缀
                    key = key.split(":", 1)[1]
                if a_type == TYPE_STRING:
                    val = strings[a_data] if a_data < len(strings) else ""
                elif a_type == 0x10:  # TYPE_INT_DEC
                    val = str(a_data)
                elif a_type == 0x12:  # TYPE_INT_BOOLEAN
                    val = "true" if a_data else "false"
                else:
                    val = strings[a_raw] if a_raw < len(strings) else ""
                attrs[key] = val
            return attrs
        off += csize
    return {}


def apk_identity(apk_path):
    """返回 (package, versionName, versionCode)，取不到为 ""。"""
    a = read_manifest_attrs(apk_path)
    return (a.get("package", ""), a.get("versionName", ""), a.get("versionCode", ""))


if __name__ == "__main__":
    import sys
    pkg, vn, vc = apk_identity(sys.argv[1])
    print("package=%s versionName=%s versionCode=%s" % (pkg, vn, vc))