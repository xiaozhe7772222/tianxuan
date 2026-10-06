package top.wkbin.tianxuan.core.common.astronomy

/**
 * 三垣 —— 天玄记忆库的三层结构。
 *
 * 三垣（紫微垣、太微垣、天市垣）与二十八宿合称「三垣二十八宿」。
 * 紫微为天帝居所，太微为朝廷政务，天市为天上街市。
 *
 * 以此构建记忆三层，让模型明确「该往哪一层写、从哪一层读」，
 * 避免把短期政务写进身份、也避免长期偏好被对话冲掉。
 */
enum class Enclosure(
    /** 垣名，如「紫微垣」。 */
    val label: String,
    /** 古义。 */
    val gloss: String,
    /** 记忆层名。 */
    val memoryLayer: String,
    /** 该层的生命周期策略。 */
    val retention: String,
    /** 写入策略：何种内容应落此层。 */
    val writePolicy: String,
    /** 读取策略：何时召回此层。 */
    val readPolicy: String,
) {
    /** 紫微垣：天帝居所，众星环拱之中枢 —— 核心记忆。 */
    ZI_WEI(
        label = "紫微垣",
        gloss = "天帝居所，众星环拱之中枢",
        memoryLayer = "核心记忆",
        retention = "永久驻留，不压缩、不外置",
        writePolicy = "身份设定、长期偏好、用户画像、不可动摇的规则",
        readPolicy = "每轮必读，常驻系统提示词",
    ),

    /** 太微垣：朝廷政务，主施行 —— 工作记忆。 */
    TAI_WEI(
        label = "太微垣",
        gloss = "朝廷政务，主施行",
        memoryLayer = "工作记忆",
        retention = "会话结束可归档，超期转入紫微垣或天市垣",
        writePolicy = "当前会话、执行计划、任务检查点、中间产物",
        readPolicy = "任务进行中按需读取，会话切换时归档",
    ),

    /** 天市垣：天上街市，主交易流通 —— 检索记忆。 */
    TIAN_SHI(
        label = "天市垣",
        gloss = "天上街市，主交易流通",
        memoryLayer = "检索记忆",
        retention = "按需进出，命中即用、不命中不驻留",
        writePolicy = "外部知识、历史归档、可外置的语料",
        readPolicy = "语义召回后注入，用毕即退",
    ),
    ;

    /** UI 上「垣名 · 记忆层」的显示串。 */
    val display: String get() = "$label · $memoryLayer"

    companion object {
        /** 按记忆层名取垣，用于配置与提示词解析。 */
        fun ofMemoryLayer(memoryLayer: String): Enclosure? =
            entries.firstOrNull { it.memoryLayer == memoryLayer }
    }
}
