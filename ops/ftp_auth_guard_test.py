"""FTP 凭据来源守卫的自测。

背景：FtpServiceManager.start() 曾经是

    val ftpPassword = preferences.readPassword(distroId)?.ifBlank { null }
    val sshPassword = sshPreferences.readPassword(distroId)?.ifBlank { null }
    val password = ftpPassword ?: sshPassword

即「FTP 没单独设密码时，自动拿 SSH 密码顶上」。这条回落在三处同时出问题：

  1. **跨服务凭据复用**：FTP 是明文协议（本实现连 AUTH/TLS 都不支持），
     把 SSH 密码喂给它，等于让一处泄漏同时失守两个服务。
  2. **界面与行为相反**：FtpSettingsScreen 在未单独设密码时显示
     「未设置密码（免密登录，客户端密码留空或填任意内容即可）」，
     但用户只要配过 SSH 密码，FTP 实际要求的就是那把 SSH 密码，
     照着界面操作会一直 530，且界面没有任何可排查线索。
  3. **不可观测**：FtpPreferences.passwordConfigured 只看 FTP 键，
     所以 UI 永远不可能提示「正在使用 SSH 密码」。

修法是让 FTP 只认 FTP 自己的密码，免密只走显式的 anonymousEnabled。

这个守卫本身也会失效——有人图省事把 `?: sshPassword` 加回来、或者
重新引入 SshPreferences 依赖、或者界面又出现「同 SSH 密码」的误导文案，
都不会有任何报错，只会在用户连不上时才发现。因此这里不查配置文件，
而是直接查源码里那几行的**实际写法**。

运行：python3 ftp_auth_guard_test.py
"""

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)

SERVICE = os.path.join(
    REPO, "runtime/src/main/java/top/wkbin/tianxuan/runtime/FtpServiceManager.kt")
RUNTIME_DI = os.path.join(
    REPO, "runtime/src/main/java/top/wkbin/tianxuan/di/runtime/KoinModule.kt")
SETTINGS_SCREEN = os.path.join(
    REPO, "feature/settings/src/main/java/top/wkbin/tianxuan/ui/settings/FtpSettingsScreen.kt")
SETTINGS_VM = os.path.join(
    REPO, "feature/settings/src/main/java/top/wkbin/tianxuan/ui/settings/FtpSettingsViewModel.kt")
SETTINGS_DI = os.path.join(
    REPO, "feature/settings/src/main/java/top/wkbin/tianxuan/di/feature/settings/KoinModule.kt")

# FtpSession.handlePass 的密码判定：密码未配置时必须拒绝一切密码登录。
SESSION = os.path.join(
    REPO, "runtime/src/main/java/top/wkbin/tianxuan/runtime/ftp/AndroidFtpServer.kt")

# WebChat 局域网页面的配对码比对。
WEBCHAT = os.path.join(
    REPO, "runtime/src/main/java/top/wkbin/tianxuan/runtime/webchat/WebChatBridgeServer.kt")
# 配对码的恒定时间比较（独立文件，见其类注释）。
PIN_VERIFIER = os.path.join(
    REPO, "runtime/src/main/java/top/wkbin/tianxuan/runtime/webchat/PinVerifier.kt")

# HostBridge 的 Bearer 密钥比对。
HOST_BRIDGE = os.path.join(
    REPO, "runtime/src/main/java/top/wkbin/tianxuan/runtime/bridge/HostBridge.kt")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def active_lines(text):
    """剥掉整行注释与行尾注释，避免「注释里写着正确写法」骗过守卫。

    只处理 // 与 /* */，够用且不引入误判：Kotlin 字符串里出现 // 的情形
    在本文件关心的几行（val 赋值、import、文案）中不存在。
    """
    out = []
    in_block = False
    for raw in text.splitlines():
        line = raw
        if in_block:
            end = line.find("*/")
            if end < 0:
                continue
            line = line[end + 2:]
            in_block = False
        while True:
            start = line.find("/*")
            if start < 0:
                break
            end = line.find("*/", start + 2)
            if end < 0:
                line = line[:start]
                in_block = True
                break
            line = line[:start] + line[end + 2:]
        cut = line.find("//")
        if cut >= 0:
            line = line[:cut]
        out.append(line)
    return "\n".join(out)


# ---- 守卫 --------------------------------------------------------------------


def assert_no_ssh_fallback():
    """FTP 的密码必须只来自 FtpPreferences，不能回落 SSH。"""
    code = active_lines(read(SERVICE))

    # 1) 不能出现「ftp 密码 ?: ssh 密码」这类合并赋值。
    #    允许的形态是 `val password = preferences.readPassword(...)`。
    bad = re.search(r"val\s+password\s*=\s*[^\n]*?:[^\n]*ssh", code, re.IGNORECASE)
    if bad:
        raise AssertionError(
            "FtpServiceManager 又把 SSH 密码拼进 FTP 凭据了："
            f"{bad.group(0).strip()!r}")

    # 2) 整个文件不得再出现 ssh 相关的读密码调用。
    for m in re.finditer(r"\bssh\w*\.readPassword\b", code, re.IGNORECASE):
        raise AssertionError(
            f"FtpServiceManager 仍在读取 SSH 密码：{m.group(0)!r}")

    # 3) password 的赋值必须真的来自 FtpPreferences。
    assign = re.search(r"val\s+password\s*=\s*([^\n]+)", code)
    if assign is None:
        raise AssertionError("FtpServiceManager 里找不到 `val password = ...` 赋值")
    if "preferences.readPassword" not in assign.group(1):
        raise AssertionError(
            "FTP 密码的来源不再是 FtpPreferences.readPassword："
            f"{assign.group(1).strip()!r}")


def assert_no_ssh_dependency():
    """构造器与 DI 都不该再注入 SshPreferences——依赖在，回落就会回来。"""
    for path, who in ((SERVICE, "FtpServiceManager"), (RUNTIME_DI, "runtime KoinModule")):
        code = read(path)
        if "SshPreferences" in active_lines(code):
            raise AssertionError(f"{who} 仍持有 SshPreferences 依赖（{path}）")

    vm = active_lines(read(SETTINGS_VM))
    if "SshPreferences" in vm:
        raise AssertionError(f"FtpSettingsViewModel 仍持有 SshPreferences 依赖（{SETTINGS_VM}）")
    if "sshPasswordConfigured" in vm:
        raise AssertionError(
            "FtpSettingsUiState 又引入了 sshPasswordConfigured——"
            "该字段既无数据来源也无展示意义，只会重新制造「界面暗示用 SSH 密码」的误导")

    di = active_lines(read(SETTINGS_DI))
    if "sshPreferences" in di:
        raise AssertionError(f"settings KoinModule 仍在为 FTP ViewModel 注入 sshPreferences（{SETTINGS_DI}）")


def assert_ui_matches_behaviour():
    """界面文案必须与「FTP 只认 FTP 密码 + 免密仅由匿名开关提供」一致。"""
    screen = read(SETTINGS_SCREEN)
    active = active_lines(screen)

    for banned in ("同 SSH 密码", "已自动使用 SSH 登录密码"):
        if banned in active:
            raise AssertionError(
                f"FTP 设置页又出现了「{banned}」——实际已经不会用 SSH 密码，文案与行为相反")

    # 「留空即可」只在开启匿名访问时才成立，绝不能再出现在无条件分支里。
    if re.search(r"免密登录，客户端密码留空", active):
        raise AssertionError(
            "FTP 设置页又声称「免密登录，客户端密码留空即可」——"
            "未开匿名时密码留空只会拿到 530，必须按 anonymousEnabled 分支给出文案")

    # 匿名开关必须真的参与密码文案判定，否则用户无从判断能否免密。
    if "settings.anonymousEnabled" not in active:
        raise AssertionError("FTP 设置页的密码文案不再参考 anonymousEnabled，用户无法判断是否可免密")


def assert_session_still_rejects_blank_password():
    """服务端兜底：未配置密码时必须拒绝一切密码登录。

    这条与上面几条互补——即便将来有人给 FTP 造出别的密码来源，
    只要 handlePass 仍要求「已配置且相等」，就不会退化成无密码放行。

    判据必须是**布尔结构**而不是子串：只查 `isNullOrBlank` 是否存在是不够的，
    `config.password.isNullOrBlank() || pass == config.password` 同样含有该调用，
    但语义正好相反（未配置密码即放行，等于向局域网开放无密码读写）。
    这正是本项在反向验证中暴露过一次的漏判，故此处按运算符分组逐一断言。
    """
    code = active_lines(read(SESSION))
    m = re.search(r"val\s+passwordMatches\s*=\s*([^\n]+)", code)
    if m is None:
        raise AssertionError("FtpSession 里找不到 passwordMatches 的判定")
    expr = m.group(1).strip()

    # 以顶层 || 切分：只要存在任何一个「仅凭密码为空就成立」的分支，即为漏洞。
    disjuncts = split_top_level(expr, "||")
    for part in disjuncts:
        negated_blank = re.search(r"!\s*config\.password\.isNullOrBlank\(\)", part)
        plain_blank = re.search(r"(?<![!=\w])config\.password\.isNullOrBlank\(\)", part)
        if plain_blank and not negated_blank:
            raise AssertionError(
                "FtpSession 存在「密码未配置即放行」的分支，FTP 会变成无密码可读写："
                f"{part.strip()!r}（完整判定 {expr!r}）")
        if re.search(r"pass\.isEmpty\(\)|arg\.isEmpty\(\)", part):
            raise AssertionError(
                f"FtpSession 放行了空密码：{part.strip()!r}（完整判定 {expr!r}）")

    if not re.search(r"pass\s*==\s*config\.password", expr):
        raise AssertionError(f"FtpSession 的密码判定不再做等值比较：{expr!r}")

    # 合取项里必须有「密码已配置」这一条，否则空密码会被 == 匹配放行。
    conjuncts = split_top_level(expr, "&&")
    if not any(re.search(r"!\s*config\.password\.isNullOrBlank\(\)", c) for c in conjuncts):
        raise AssertionError(
            "FtpSession 的密码判定缺少「密码已配置」的合取条件，"
            f"空密码可能被等值比较放行：{expr!r}")


def assert_webchat_pin_compared_in_constant_time():
    """WebChat 配对码必须走恒定时间比较，且两条校验路径共用同一实现。

    配对码是 6 位十进制（约 20 bit）。字符串 `==` 首个不同字符即返回，
    攻击者按响应耗时逐位收敛即可，20 次量级而非 10^6 次。
    服务绑定 InetSocketAddress(port)（通配地址）且响应头带
    `Access-Control-Allow-Origin: *`，服务在监听期间长期常驻——
    这是稳定的远程计时旁路信道，不是纯理论风险。

    比对逻辑在 PinVerifier 中（独立文件，理由见其类注释）。
    """
    verifier = active_lines(read(PIN_VERIFIER))
    if "MessageDigest.isEqual" not in verifier:
        raise AssertionError(
            "PinVerifier 的配对码比较不再是恒定时间——"
            "改回 `==` 会让 6 位配对码被计时旁路逐位猜出")

    if not re.search(r"fun\s+matches\s*\(", verifier):
        raise AssertionError("PinVerifier 找不到 matches 入口")

    # 必须拒绝空候选与空期望，避免未设置 pin 时被空 token 通过。
    if not re.search(r"candidate\s*==\s*null", verifier):
        raise AssertionError("PinVerifier 未拒绝缺失的候选配对码")
    if not re.search(r"expected\.isEmpty\(\)", verifier):
        raise AssertionError(
            "PinVerifier 未拒绝空期望配对码——pin 未设置时会被空 token 通过")

    # 服务端不得再出现绕过 PinVerifier 的原始字符串比对。
    server = active_lines(read(WEBCHAT))
    for m in re.finditer(r"token\s*[!=]=\s*[^\n]*?pinCode", server):
        raise AssertionError(
            f"WebChatBridgeServer 存在绕过 PinVerifier 的比对：{m.group(0).strip()!r}")

    # 两条校验路径（isAuthenticated 与 SessionBootstrapHandler）都必须走 PinVerifier。
    uses = len(re.findall(r"PinVerifier\.matches\(", server))
    if uses < 2:
        raise AssertionError(
            f"WebChatBridgeServer 仅有 {uses} 处调用 PinVerifier.matches——"
            "预期 isAuthenticated 与 SessionBootstrapHandler 各一处，"
            "可能有校验路径仍在校验原始字符串")

    # 未知扩展名的静态资源判定必须与 MIME 表同源，不得出现两套扩展名清单。
    if "WebChatAssets.mimeTypeOrNull" not in server:
        raise AssertionError(
            "WebChatBridgeServer 的静态资源判定未走 WebChatAssets.mimeTypeOrNull——"
            "分成两份扩展名清单会在新增类型时错配（按资源取文件却回落 index.html）")


def split_top_level(expr, operator):
    """按顶层运算符切分表达式，忽略括号/引号内部的出现。

    仅够本项目用：判定的两层结构就是 `A && B` 与若干 `C || D`，
    不引入完整表达式解析器，避免守卫本身成为脆弱点。
    """
    parts, depth, quote, current = [], 0, None, ""
    i = 0
    while i < len(expr):
        ch = expr[i]
        if quote:
            if ch == quote and expr[i - 1] != "\\":
                quote = None
            current += ch
        elif ch in ("'", '"'):
            quote = ch
            current += ch
        elif ch in "([{":
            depth += 1
            current += ch
        elif ch in ")]}":
            depth -= 1
            current += ch
        elif depth == 0 and expr.startswith(operator, i):
            parts.append(current)
            current = ""
            i += len(operator)
            continue
        else:
            current += ch
        i += 1
    parts.append(current)
    return parts


def assert_host_bridge_key_compared_in_constant_time():
    """HostBridge 的 Bearer 密钥必须走恒定时间比较。

    **这不是可利用漏洞**，与 WebChat 配对码要分开定性：密钥是
    UUID 去横杠后的 128 bit 随机值，逐字节计时旁路在数学上不可穷举；
    且服务只监听 127.0.0.1，能连上的攻击者已经在设备内。

    之所以仍然守卫，是为了**防止密钥长度被调短时无声退化**：
    `checkAuth` 里同一行代码，今天面对 128 bit 是安全的，明天若有人
    为了「方便调试」把 bridgeKey 换成 6 位数字，`==` 立刻变成可利用的
    计时旁路，而没有任何编译期或运行期提示。让写法与
    ShellCommandFactory.verifyPin / PinVerifier 保持一致，退化就不可能静默发生。
    """
    code = active_lines(read(HOST_BRIDGE))
    m = re.search(r"fun\s+checkAuth\s*\([^)]*\)[^\n]*\{([\s\S]*?)\n\s{4}\}", code)
    body = m.group(1) if m else code
    if "MessageDigest.isEqual" not in body:
        raise AssertionError(
            "HostBridge.checkAuth 不再使用恒定时间比较——"
            "密钥若被调短（例如换成 6 位数字）会立刻变成可利用的计时旁路")
    if re.search(r"\btoken\s*==\s*bridgeKey\b", body):
        raise AssertionError("HostBridge.checkAuth 回退成了 `token == bridgeKey`")


CHECKS = [
    assert_no_ssh_fallback,
    assert_no_ssh_dependency,
    assert_ui_matches_behaviour,
    assert_session_still_rejects_blank_password,
    assert_webchat_pin_compared_in_constant_time,
    assert_host_bridge_key_compared_in_constant_time,
]


def main():
    failures = []
    for check in CHECKS:
        try:
            check()
            print(f"  PASS  {check.__name__}")
        except AssertionError as e:
            failures.append(f"{check.__name__}: {e}")
            print(f"  FAIL  {check.__name__}")
            print(f"        {e}")

    print()
    if failures:
        print(f"FTP 凭据来源守卫未通过，共 {len(failures)} 项：", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1
    print(f"FTP 凭据来源守卫全部通过（{len(CHECKS)} 项）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
