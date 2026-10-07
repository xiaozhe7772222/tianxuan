#!/usr/bin/env python3
"""括号/字符串感知的 Kotlin 符号提取器。

为什么需要它
------------
审计并发、审批策略、枚举穷尽性时，最常见的动作是"把某个函数完整看一遍"。
用 `grep -A` / 正则 / `index('{')` + 花括号计数的做法在 Kotlin 上会**静默出错**，
而且错得很像真的。本项目已实际踩过三次同样的坑：

1. **枚举覆盖计数错**：`@SerialName("compress") COMPRESS,` 的注解与成员同行，
   正则 `^\\s*([A-Z_]+)\\s*[,;]` 匹配不到 COMPRESS，于是"看起来漏了一个分支"。
2. **作用域判断错**：`fun f()` 里出现 `.join()`，看起来是"非 suspend 函数调用挂起函数"。
   实际它在 `scope.launch { ... }` 内部，完全合法。行级正则看不到 lambda 嵌套。
3. **分支截断**：`private fun d(v: String): String? = if (x(v)) { decrypt(v) } else { v }`
   是**表达式体**函数。`index('{')` 找到的是 `if` 的块，花括号配对在 `if` 结束时就归零，
   **`else` 被整段截掉**——于是"发现了一个不存在的缺陷"。

正确做法
--------
- 块体：配对花括号时必须**跳过字符串字面量与转义**，否则 `"{"` 会污染计数。
- 表达式体：识别「顶层 `=`」，再按**缩进层级**消费续行，不做花括号配对。

用法
----
    python3 scripts/extract_kotlin_symbol.py <file.kt> <SymbolName> [<SymbolName> ...]

    # 提取类/对象整体（按 class/object/interface 关键字识别）
    python3 scripts/extract_kotlin_symbol.py path/To.kt MyClass

    # 只列出全部符号名（不打印正文），便于快速定位
    python3 scripts/extract_kotlin_symbol.py --list path/To.kt

退出码：0 全部找到；1 有符号未找到。

权威性提示
----------
本工具用于**阅读**，不用于**判定合法性**。判定「枚举是否穷尽」请用 Kotlin 编译器本身：
往枚举里注入一个探针成员（如 `@SerialName("__probe__") __PROBE__`），
编译器会精确列出所有 `'when' expression must be exhaustive` 的位置。
编译器比任何文本工具都权威。
"""

from __future__ import annotations

import re
import sys

FUN_RE = r"(?:^|\n)[ \t]*(?:@\w+[^\n]*\n[ \t]*)*(?:private |internal |public |protected |override |open |actual )*(?:suspend )?fun\s+"
CLASS_RE = r"(?:^|\n)[ \t]*(?:@\w+[^\n]*\n[ \t]*)*(?:private |internal |public |protected |open |sealed |abstract |data |value |annotation )*(?:class|object|interface)\s+"


def _skip_string(src: str, k: int) -> int:
    """k 指向引号；返回闭合引号之后的位置。支持转义与三引号。"""
    if src.startswith('"""', k):
        end = src.find('"""', k + 3)
        return len(src) if end < 0 else end + 3
    quote = src[k]
    k += 1
    while k < len(src):
        if src[k] == "\\":
            k += 2
            continue
        if src[k] == quote:
            return k + 1
        if src[k] == "\n":
            return k
        k += 1
    return k


def balanced(src: str, start: int) -> str:
    """start 指向 '{'；返回配对到闭合处的整个块（跳过字符串与注释）。"""
    depth = 0
    k = start
    while k < len(src):
        c = src[k]
        if c == '"':
            k = _skip_string(src, k)
            continue
        if c == "/" and src[k : k + 2] == "//":
            nl = src.find("\n", k)
            k = len(src) if nl < 0 else nl
            continue
        if c == "/" and src[k : k + 2] == "/*":
            end = src.find("*/", k)
            k = len(src) if end < 0 else end + 2
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[start : k + 1]
        k += 1
    return src[start:]


def _indent_of(src: str, pos: int) -> int:
    """返回 pos **所在行**的行首缩进宽度。pos 可以是行内任意位置。"""
    line_start = src.rfind("\n", 0, pos) + 1
    k = line_start
    while k < len(src) and src[k] in " \t":
        k += 1
    return k - line_start


def _expression_body_end(src: str, eq_pos: int, decl_indent: int) -> int:
    """从顶层 '=' 起消费续行，返回表达式结束的**索引**（不含换行）。

    只要下一行缩进比声明行深（或为空白行）就认为仍是同一表达式的一部分。
    带硬性迭代上限：本函数只用于**阅读**，绝不允许因输入异常而挂起。
    """
    k = eq_pos
    for _ in range(100_000):
        nl = src.find("\n", k)
        if nl < 0:
            return len(src)
        nxt_start = nl + 1
        nxt_end = src.find("\n", nxt_start)
        nxt = src[nxt_start : nxt_end if nxt_end > 0 else len(src)]
        if nxt.strip() == "" and nxt_end > 0:
            k = nl
            continue
        if _indent_of(src, nxt_start) > decl_indent:
            k = nl
            continue
        return nl
    return len(src)


def _skip_balanced_parens(src: str, k: int) -> int:
    """k 指向 '('；返回配对 ')' 之后的位置。跳过字符串。"""
    depth = 0
    while k < len(src):
        c = src[k]
        if c == '"':
            k = _skip_string(src, k)
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return k + 1
        k += 1
    return k


def extract(src: str, name: str) -> str | None:
    """返回符号（函数 / 类 / 对象 / 接口）的完整源码文本；找不到返回 None。

    关键：函数的**参数列表必须整体跳过**。否则默认值 `= null` 会被误判为
    表达式体函数的分隔符，函数体根本不会被读到（这是本脚本初版的实际 bug）。
    """
    for pattern in (FUN_RE, CLASS_RE):
        m = re.search(pattern + re.escape(name) + r"\b", src)
        if not m:
            continue
        start = m.start()
        decl_indent = _indent_of(src, start + 1)
        angle = 0
        k = m.end()
        while k < len(src):
            c = src[k]
            if c == '"':
                k = _skip_string(src, k)
                continue
            if c == "(" and pattern is FUN_RE:
                # 跳过整个参数列表（含默认值），再继续找函数体
                k = _skip_balanced_parens(src, k)
                continue
            if c == "<" and src[k : k + 2] != "<=":
                angle += 1
            elif c == ">" and src[k - 1 : k] != "-":
                angle = max(0, angle - 1)
            elif angle == 0:
                if c == "{":
                    return src[start : k + len(balanced(src, k))]
                if c == "=" and src[k : k + 2] not in ("==", "=>") and src[k - 1] not in "=!<>+-*/%":
                    return src[start : _expression_body_end(src, k, decl_indent)]
                if c == "\n":
                    # 声明结束还没找到 body（如 abstract/expect）：返回单行
                    return src[start:k]
            k += 1
    return None


def list_symbols(src: str) -> list[str]:
    names = []
    for pattern in (FUN_RE, CLASS_RE):
        for m in re.finditer(pattern + r"([A-Za-z_]\w*)", src):
            names.append(m.group(1))
    return names


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    path = argv[1]
    src = open(path, encoding="utf-8").read()
    if argv[1] == "--list" or (len(argv) > 2 and argv[1] == "--list"):
        for n in list_symbols(src):
            print(n)
        return 0
    names = argv[2:]
    if not names:
        print(__doc__)
        return 2
    missing = 0
    for name in names:
        body = extract(src, name)
        line = src[: src.index(body)].count("\n") + 1 if body and body in src else "?"
        print("\n" + "=" * 72)
        print(f"=== {name}  ({path}:L{line}) ===")
        print("=" * 72)
        print(body if body else "  (未找到)")
        if not body:
            missing += 1
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
