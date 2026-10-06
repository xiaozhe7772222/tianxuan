package top.wkbin.tianxuan.core.common.astronomy

/**
 * 北斗七星 —— 天玄的七曜「门」。
 *
 * 《史记·天官书》："斗为帝车，运于中央，临制四乡。分阴阳，建四时，均五行，
 * 移节度，定诸纪，皆系于斗。"
 *
 * 七星对应应用的七条主干能力：这一层是**用户看得见的门**，
 * 门背后的实现构件由二十八宿承载（见 [Mansion]），两层不重复、不串位。
 *
 * 前四星（天枢→天权）为**魁**（斗勺），主承；后三星（玉衡→摇光）为**杓**（斗柄），主施。
 */
enum class BeiDou(
    /** 星名，如「天枢」。 */
    val starName: String,
    /** 拜耳编号与西名，如「α Dubhe」。 */
    val bayer: String,
    /** 道门别称（贪狼、巨门……破军）。 */
    val alias: String,
    /** 自斗口起算的序次，1..7。 */
    val order: Int,
    /** 所属部位：魁或杓。 */
    val part: DipperPart,
    /** 能力门名，如「智枢」「智坊」。 */
    val gateName: String,
    /** 门的英文标识，用于路由与日志 tag 的稳定 ID。 */
    val gateKey: String,
    /** 取义，一句话说明为何以此星配此门。 */
    val rationale: String,
) {
    /** 天枢：众机之枢，星纪由此推排。 */
    TIAN_SHU(
        starName = "天枢",
        bayer = "α Dubhe",
        alias = "贪狼",
        order = 1,
        part = DipperPart.KUI,
        gateName = "智枢",
        gateKey = "agent",
        rationale = "众机之枢，星纪由此推排",
    ),

    /** 天璇：攻玉成器，造作之所。 */
    TIAN_XUAN(
        starName = "天璇",
        bayer = "β Merak",
        alias = "巨门",
        order = 2,
        part = DipperPart.KUI,
        gateName = "智坊",
        gateKey = "workspace",
        rationale = "攻玉成器，造作之所",
    ),

    /** 天玑：直面天机，亲执枢要。 */
    TIAN_JI(
        starName = "天玑",
        bayer = "γ Phecda",
        alias = "禄存",
        order = 3,
        part = DipperPart.KUI,
        gateName = "终端",
        gateKey = "terminal",
        rationale = "直面天机，亲执枢要",
    ),

    /** 天权：权衡缓急，分派调度。 */
    TIAN_QUAN(
        starName = "天权",
        bayer = "δ Megrez",
        alias = "文曲",
        order = 4,
        part = DipperPart.KUI,
        gateName = "工作流",
        gateKey = "workflow",
        rationale = "权衡缓急，分派调度",
    ),

    /** 玉衡：北斗最亮之星，平准得失。 */
    YU_HENG(
        starName = "玉衡",
        bayer = "ε Alioth",
        alias = "廉贞",
        order = 5,
        part = DipperPart.BIAO,
        gateName = "版本",
        gateKey = "git",
        rationale = "平准得失，铢两悉称",
    ),

    /** 开阳：开牖见天，纳四境之光。 */
    KAI_YANG(
        starName = "开阳",
        bayer = "ζ Mizar",
        alias = "武曲",
        order = 6,
        part = DipperPart.BIAO,
        gateName = "浏览器",
        gateKey = "browser",
        rationale = "开牖见天，纳四境之光",
    ),

    /** 摇光：摇动枢机，光被全域。 */
    YAO_GUANG(
        starName = "摇光",
        bayer = "η Alkaid",
        alias = "破军",
        order = 7,
        part = DipperPart.BIAO,
        gateName = "设置",
        gateKey = "settings",
        rationale = "摇动枢机，光被全域",
    ),
    ;

    /** UI 主标题：星名，如「天枢」。 */
    val title: String get() = starName

    /** UI 副题：星名 · 门名，如「天枢 · 智枢」。 */
    val subtitle: String get() = "$starName · $gateName"

    companion object {
        /** 按序次取星（1..7），越界返回 null。 */
        fun ofOrder(order: Int): BeiDou? = entries.firstOrNull { it.order == order }

        /** 按门标识取星，用于路由反查。 */
        fun ofGateKey(gateKey: String): BeiDou? = entries.firstOrNull { it.gateKey == gateKey }

        /** 按星名取星，用于文案解析。 */
        fun ofStarName(starName: String): BeiDou? = entries.firstOrNull { it.starName == starName }
    }
}

/** 北斗的部位：魁为斗勺（前四星），杓为斗柄（后三星）。 */
enum class DipperPart(val label: String, val gloss: String) {
    /** 魁：斗勺四星，主承。 */
    KUI(label = "魁", gloss = "斗勺四星，主承"),

    /** 杓：斗柄三星，主施。 */
    BIAO(label = "杓", gloss = "斗柄三星，主施"),
}
