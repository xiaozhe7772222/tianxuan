package top.wkbin.tianxuan.core.model

/**
 * 天玄 · 社区坐标
 *
 * 内测期用户选择「本地下载导入」，官方离线插件包不在应用商店上架，
 * 由用户自行下载后导入。因此交流群是获取离线包与技术支持的主入口，
 * 群号属于应用身份的一部分，必须只有一个来源。
 *
 * 放在 core.model 是因为它 requires 为空（纯 Kotlin，不碰任何平台 API），
 * feature.* 与 core.network 都能直接依赖，不会引入反向耦合。
 *
 * 历史教训：群号曾以字面量散落在 6 个文件 13 处（含中英文资源与
 * SettingsLocalization 的匹配键），改号必然漏改。现在统一读这里。
 */
object Community {
    /** 内测交流群。发布前请确认与实际群一致。 */
    const val QQ_GROUP_ID: String = "1064036647"

    /** 一键加群 scheme。uin 即群号。 */
    const val QQ_GROUP_URI: String =
        "mqqapi://card/show_pslcard?src_type=internal&version=1" +
            "&uin=$QQ_GROUP_ID&card_type=group&source=qrcode"

    /** 群号展示文案（中文）。settings 模块的本地化映射键与之一致。 */
    const val QQ_GROUP_LABEL_ZH: String = "群号: $QQ_GROUP_ID · 点击一键加群 / 复制群号"

    /** 群号展示文案（英文）。 */
    const val QQ_GROUP_LABEL_EN: String =
        "Group number: $QQ_GROUP_ID · Click to add group/copy group number"

    /** 加群入口文案（中文）。 */
    const val QQ_JOIN_LABEL_ZH: String = "加入 QQ 交流群 ($QQ_GROUP_ID)"

    /** 加群入口文案（英文）。 */
    const val QQ_JOIN_LABEL_EN: String = "Join the QQ group ($QQ_GROUP_ID)"
}