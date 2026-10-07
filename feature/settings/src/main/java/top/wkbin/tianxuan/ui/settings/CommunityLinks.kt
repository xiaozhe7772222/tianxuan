package top.wkbin.tianxuan.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import top.wkbin.tianxuan.core.model.Community

/** 打开外部链接；无可用浏览器时复制到剪贴板，别让入口变成死路。 */
internal fun openBrowser(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        // 走 ClipboardSupport 的统一实现（含「剪贴板不可用」兜底）。
        // 本文件原有一个同名私有 `Context.copyToClipboard(label, text)` 重载，
        // 与公共函数同名不同签名且静默吞掉失败——同一仓库出现两套行为不一致的
        // 同名实现，正是 ClipboardSupport 注释里要消除的重复。
        copyToClipboard(context, url, "URL", "已复制链接：$url")
    }
}

/** 一键加群；未装 QQ 时兜底复制群号。群号来自 [Community]，不在此处另写常量。 */
internal fun joinQqGroup(context: Context) {
    val uri = Uri.parse(Community.QQ_GROUP_URI)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        copyToClipboard(
            context,
            Community.QQ_GROUP_ID,
            "天玄官方交流群",
            "已复制 QQ 群号：${Community.QQ_GROUP_ID}，可打开 QQ 搜索加入",
        )
    }
}