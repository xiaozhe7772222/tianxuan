package top.wkbin.tianxuan.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import top.wkbin.tianxuan.core.model.Community

/** 打开外部链接；无可用浏览器时复制到剪贴板，别让入口变成死路。 */
internal fun openBrowser(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        context.copyToClipboard("URL", url)
        Toast.makeText(context, "已复制链接：$url", Toast.LENGTH_LONG).show()
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
        context.copyToClipboard("天玄官方交流群", Community.QQ_GROUP_ID)
        Toast.makeText(
            context,
            "已复制 QQ 群号：${Community.QQ_GROUP_ID}，可打开 QQ 搜索加入",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}