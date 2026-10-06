package top.wkbin.tianxuan.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import top.wkbin.tianxuan.core.model.Community
import top.wkbin.tianxuan.feature.home.R

/**
 * 加群入口。群号来自 [Community]，不在此处另写常量。
 */
internal fun joinQqGroup(context: Context) {
    val uri = Uri.parse(Community.QQ_GROUP_URI)
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        // 未装QQ：兜底复制群号，别让入口变成死路
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.home_qq_clipboard_label), Community.QQ_GROUP_ID),
        )
        Toast.makeText(
            context,
            context.getString(R.string.home_qq_copied, Community.QQ_GROUP_ID),
            Toast.LENGTH_LONG,
        ).show()
    }
}