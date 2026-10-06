package top.wkbin.tianxuan.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * 复制文本到剪贴板并给出轻提示。
 *
 * 此前这段在 CcSwitchScreen / FtpSettingsScreen / SshSettingsScreen 各抄了一份，
 * 差别只在剪贴板标签（`TianXuan` / `FTP URL` / `SSH command`）—— 标签本身是
 * 调用方的语境，不该由公共函数替所有人决定。抄三份的直接代价是：改提示文案或
 * 换剪贴板标签时要改三处，漏一处就出现行为不一致的复制按钮，而这类差异
 * 只在用户点击时才发现。
 *
 * @param label 剪贴板内容标签，供用户识别这条内容的来源
 * @param toast 复制成功后的提示文案，由调用方按语境给出（复制的是凭据还是地址）
 */
internal fun copyToClipboard(context: Context, text: String, label: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    // 拿不到剪贴板服务时如实告知：静默失败会让用户以为已复制，
    // 然后在粘贴处找不到内容而反复排查。
    if (clipboard == null) {
        Toast.makeText(context, "系统剪贴板不可用", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}