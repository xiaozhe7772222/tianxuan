package top.wkbin.tianxuan.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.wkbin.tianxuan.core.model.Community
import top.wkbin.tianxuan.feature.settings.R

/**
 * 动态文案的翻译规则。
 *
 * [SettingsLocalization.legacyStringResource] 是「中文原文 → 资源ID」的静态表，
 * 只能覆盖编译期已知的字面量。像「3 个模型」「群号: xxx」这类**运行时拼出来的
 * 字符串**不在表里，需要在此按结构匹配后注入参数。
 *
 * 群号尤其不能进静态表：它是[Community.QQ_GROUP_ID] 的运行时值，
 * 进表就等于多一份要同步的副本，改群号必然漏改（历史上就是这么漏的）。
 */
@Composable
internal fun resolveDynamicString(source: String): String? {
    // NOTE: raw 字符串不做转义处理，此处必须写 \d；写成 \\d 会匹配字面 "\d" 导致永不命中。
    matchOne(source, """(\d+) 套系统 · (.+)""") {
        return stringResource(R.string.settings_dynamic_system_mode, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """(\d+) 套系统""") {
        return stringResource(R.string.settings_dynamic_system_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) 个模型 · (\d+) 技能""") {
        return stringResource(R.string.settings_dynamic_model_skill_count, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """(\d+) 个模型""") {
        return stringResource(R.string.settings_dynamic_model_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) 个技能""") {
        return stringResource(R.string.settings_dynamic_skill_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) 个""") {
        return stringResource(R.string.settings_dynamic_item_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) 轮""") {
        return stringResource(R.string.settings_dynamic_round_count, it.groupValues[1])
    }
    matchOne(source, """下载中：(.+) / (.+) MB""") {
        return stringResource(R.string.settings_dynamic_download_progress, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """已下载：(.+) MB""") {
        return stringResource(R.string.settings_dynamic_downloaded, it.groupValues[1])
    }
    if (source.startsWith("发现新版本 v")) {
        return stringResource(R.string.settings_dynamic_new_version, source.removePrefix("发现新版本 v"))
    }
    if (source.startsWith("确定删除 ") && source.endsWith("？")) {
        return stringResource(
            R.string.settings_dynamic_delete_confirm,
            source.removePrefix("确定删除 ").removeSuffix("？"),
        )
    }
    matchOne(source, """成功探测到 (\d+) 个工具""") {
        return stringResource(R.string.settings_dynamic_tool_count, it.groupValues[1])
    }
    matchOne(
        source,
        """轮次用尽时先让模型收束并记录进度，再自动续跑，无需用户点击继续；总预算上限 (\d+) 轮""",
    ) {
        return stringResource(R.string.settings_text_0340, it.groupValues[1])
    }
    matchOne(source, """(\d+) 次""") {
        return stringResource(R.string.settings_text_0341, it.groupValues[1])
    }
    // 群号匹配到结构后，一律注入 Community 的当前值而非正则捕获组：
    // 调用方传的可能是过期文案，用真值才不会出现两个群号并存
    matchOne(source, """群号: (\d+) · .+""") {
        return stringResource(R.string.settings_dynamic_qq_group, Community.QQ_GROUP_ID)
    }
    matchOne(source, """加入 QQ 交流群 \((\d+)\)""") {
        return stringResource(R.string.settings_dynamic_qq_join, Community.QQ_GROUP_ID)
    }
    return null
}

/** 整串匹配才回调，否则 null。避免前缀命中导致误翻译。 */
private inline fun matchOne(source: String, pattern: String, block: (MatchResult) -> String): String? =
    Regex(pattern).matchEntire(source)?.let(block)