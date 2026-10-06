package top.wkbin.tianxuan.ui.browser.snapshot

import top.wkbin.tianxuan.core.browser.PageSnapshot

data class SnapshotSheetState(
    val url: String,
    val title: String,
    val snapshot: PageSnapshot,
)
