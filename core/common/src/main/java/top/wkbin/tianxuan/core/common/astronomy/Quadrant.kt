package top.wkbin.tianxuan.core.common.astronomy

/**
 * 四象 —— 二十八宿所分的四个方位域。
 *
 * 《礼记·曲礼上》："行前朱鸟而后玄武，左青龙而右白虎。"
 * 每一象领七宿，配一行、一时、一色，是天玄底层模块的分域依据。
 */
enum class Quadrant(
    /** 象名，如「东方青龙」。 */
    val label: String,
    /** 方位：东南西北。 */
    val direction: String,
    /** 五行所属。 */
    val element: String,
    /** 所主时令。 */
    val season: String,
    /** 所主之色。 */
    val color: String,
    /** 该象所对应的技术分层名。 */
    val layerLabel: String,
) {
    /** 东方青龙：属木，主春生发 —— 智能体引擎层。 */
    AZURE_DRAGON(
        label = "东方青龙",
        direction = "东",
        element = "木",
        season = "春",
        color = "青",
        layerLabel = "智能体引擎层",
    ),

    /** 北方玄武：属水，主冬藏纳 —— 运行时底层。 */
    BLACK_TORTOISE(
        label = "北方玄武",
        direction = "北",
        element = "水",
        season = "冬",
        color = "黑",
        layerLabel = "运行时底层",
    ),

    /** 西方白虎：属金，主秋收敛 —— 工具与集成层。 */
    WHITE_TIGER(
        label = "西方白虎",
        direction = "西",
        element = "金",
        season = "秋",
        color = "白",
        layerLabel = "工具与集成层",
    ),

    /** 南方朱雀：属火，主夏显扬 —— 表现与体验层。 */
    VERMILION_BIRD(
        label = "南方朱雀",
        direction = "南",
        element = "火",
        season = "夏",
        color = "赤",
        layerLabel = "表现与体验层",
    ),
    ;

    /** 该象领七宿。 */
    val mansions: List<Mansion> get() = Mansion.entries.filter { it.quadrant == this }

    companion object {
        /** 按方位字取象，用于配置解析与日志反查。 */
        fun ofDirection(direction: String): Quadrant? =
            entries.firstOrNull { it.direction == direction }
    }
}
