package top.wkbin.tianxuan.core.common.astronomy

/**
 * 二十八宿 —— 天玄的宿位「器」。
 *
 * 《尚书考灵曜》："二十八宿，天元气，万物之精也。"
 * 宿（xiù）即「舍」，是日月五星运行途中的馆舍，古人用它们作天球的坐标刻度。
 *
 * 距度依《淮南子·天文训》「星分度」：东方 75¼（含箕宿所带四分一）、
 * 北方 98、西方 80、南方 112，四象合计 365¼ 度，合周天（见 [Mansion.degree]）。
 *
 * 与北斗七星的关系：北斗是**门**（能力入口），二十八宿是门后的**器**（实现构件）。
 * 每宿绑定一个构件与一项可观测指标，日志与诊断面板（星图）按宿分区。
 */
enum class Mansion(
    /** 宿名，如「角」。 */
    val starName: String,
    /** 所属四象。 */
    val quadrant: Quadrant,
    /** 全二十八宿中的序次，1..28。 */
    val order: Int,
    /** 七曜配属。 */
    val luminary: Luminary,
    /** 所配之禽，如「蛟」。 */
    val beast: String,
    /** 距度（《淮南子·天文训》），箕宿为 11.25。 */
    val degree: Float,
    /** 所绑定的实现构件名。 */
    val component: String,
    /** 该宿承载的可观测指标名。 */
    val metric: String,
    /** 取义：为何以此宿配此构件。 */
    val gloss: String,
) {
    // ── 东方青龙七宿（七十五度又四分一，含箕宿所带）：智能体引擎层 ──────────

    /** 角木蛟：龙角，二十八宿之首，古称「天门」。 */
    JIAO(
        starName = "角", quadrant = Quadrant.AZURE_DRAGON, order = 1,
        luminary = Luminary.JUPITER, beast = "蛟", degree = 12f,
        component = "HarnessLoop", metric = "Agent 轮次",
        gloss = "星纪之元，众宿由此起算",
    ),

    /** 亢金龙：龙颈，承上启下之要害。 */
    KANG(
        starName = "亢", quadrant = Quadrant.AZURE_DRAGON, order = 2,
        luminary = Luminary.VENUS, beast = "龙", degree = 9f,
        component = "ProviderClient", metric = "协议请求数",
        gloss = "吞吐之颈，承上接下",
    ),

    /** 氐土貉：龙胸、天根，《史记·律书》"氐者，言万物皆至也"。 */
    DI(
        starName = "氐", quadrant = Quadrant.AZURE_DRAGON, order = 3,
        luminary = Luminary.SATURN, beast = "貉", degree = 15f,
        component = "AgentContext", metric = "上下文令牌数",
        gloss = "万物皆至，是为根柢",
    ),

    /** 房日兔：龙腹，古称「天驷」，主消化万物。 */
    FANG(
        starName = "房", quadrant = Quadrant.AZURE_DRAGON, order = 4,
        luminary = Luminary.SUN, beast = "兔", degree = 5f,
        component = "ToolExecutor", metric = "工具调用数",
        gloss = "天驷负载，消化施行",
    ),

    /** 心月狐：龙心，明察之主。 */
    XIN(
        starName = "心", quadrant = Quadrant.AZURE_DRAGON, order = 5,
        luminary = Luminary.MOON, beast = "狐", degree = 5f,
        component = "ApprovalPolicyEngine", metric = "审批决策数",
        gloss = "明断之心，察而后许",
    ),

    /** 尾火虎：龙尾，扫荡四方。 */
    WEI(
        starName = "尾", quadrant = Quadrant.AZURE_DRAGON, order = 6,
        luminary = Luminary.MARS, beast = "虎", degree = 18f,
        component = "SubagentOrchestrator", metric = "子智能体并发",
        gloss = "尾扫八荒，并力并举",
    ),

    /** 箕水豹：簸箕，风伯，扬清激浊。箕宿带四分一，以合周天 365¼ 度。 */
    JI(
        starName = "箕", quadrant = Quadrant.AZURE_DRAGON, order = 7,
        luminary = Luminary.MERCURY, beast = "豹", degree = 11.25f,
        component = "CompactionManager", metric = "压缩次数",
        gloss = "簸扬筛汰，留精去冗",
    ),

    // ── 北方玄武七宿（九十八度）：运行时底层 ──────────────────────────────

    /** 斗木獬：南斗，与北斗共掌生死，又称「天庙」。 */
    DOU(
        starName = "斗", quadrant = Quadrant.BLACK_TORTOISE, order = 8,
        luminary = Luminary.JUPITER, beast = "獬", degree = 26f,
        component = "ProcessRegistry", metric = "进程数",
        gloss = "掌管生死，度量众命",
    ),

    /** 牛金牛：牵牛，负载引重。 */
    NIU(
        starName = "牛", quadrant = Quadrant.BLACK_TORTOISE, order = 9,
        luminary = Luminary.VENUS, beast = "牛", degree = 8f,
        component = "PRoot", metric = "沙箱启动耗时",
        gloss = "负重致远，承载山河",
    ),

    /** 女土蝠：婺女，主织纫布帛。 */
    NV(
        starName = "女", quadrant = Quadrant.BLACK_TORTOISE, order = 10,
        luminary = Luminary.SATURN, beast = "蝠", degree = 12f,
        component = "WorkspaceFileService", metric = "文件操作数",
        gloss = "经纬织理，成章成幅",
    ),

    /** 虚日鼠：虚宿主冬，《史记·天官书》"虚为哭泣之事"，主死丧、庙堂，虚而能受。 */
    XU(
        starName = "虚", quadrant = Quadrant.BLACK_TORTOISE, order = 11,
        luminary = Luminary.SUN, beast = "鼠", degree = 10f,
        component = "StorageManager", metric = "存储回收量",
        gloss = "虚而能受，肃清冗积",
    ),

    /** 危月燕：高危，屋脊之上，主不安与戒惧。 */
    WEI_NORTH(
        starName = "危", quadrant = Quadrant.BLACK_TORTOISE, order = 12,
        luminary = Luminary.MOON, beast = "燕", degree = 17f,
        component = "PrivilegeManager", metric = "特权调用数",
        gloss = "居高临危，戒慎守护",
    ),

    /** 室火猪：营室、玄宫，主营造屋室以御严冬。 */
    SHI(
        starName = "室", quadrant = Quadrant.BLACK_TORTOISE, order = 13,
        luminary = Luminary.MARS, beast = "猪", degree = 16f,
        component = "RootFS", metric = "容器装配耗时",
        gloss = "营室筑基，以御外侵",
    ),

    /** 壁水貐：东壁，《晋书·天文志》"主文章，天下图书之秘府"。 */
    BI(
        starName = "壁", quadrant = Quadrant.BLACK_TORTOISE, order = 14,
        luminary = Luminary.MERCURY, beast = "貐", degree = 9f,
        component = "CheckpointStore", metric = "检查点数",
        gloss = "壁藏图籍，可复可还",
    ),

    // ── 西方白虎七宿（八十度）：工具与集成层 ──────────────────────────────

    /** 奎木狼：天之武库（《史记》正义"奎，天之府库"），主兵禁。 */
    KUI(
        starName = "奎", quadrant = Quadrant.WHITE_TIGER, order = 15,
        luminary = Luminary.JUPITER, beast = "狼", degree = 16f,
        component = "ToolRegistry", metric = "工具注册数",
        gloss = "武库藏兵，出纳有禁",
    ),

    /** 娄金狗：主聚众、牧养、苑囿。 */
    LOU(
        starName = "娄", quadrant = Quadrant.WHITE_TIGER, order = 16,
        luminary = Luminary.VENUS, beast = "狗", degree = 12f,
        component = "McpManager", metric = "MCP 连接数",
        gloss = "聚众牧养，统御外邦",
    ),

    /** 胃土雉：仓廪，主受纳储藏。 */
    WEI_WEST(
        starName = "胃", quadrant = Quadrant.WHITE_TIGER, order = 17,
        luminary = Luminary.SATURN, beast = "雉", degree = 14f,
        component = "InstallTransactionManager", metric = "安装事务数",
        gloss = "仓廪受纳，出入有度",
    ),

    /** 昴日鸡：昴星团（髦头），主明察四方 —— 开阳门后之器。 */
    MAO(
        starName = "昴", quadrant = Quadrant.WHITE_TIGER, order = 18,
        luminary = Luminary.SUN, beast = "鸡", degree = 11f,
        component = "WebViewTabPool", metric = "页面加载数",
        gloss = "明照四境，各安其位",
    ),

    /** 毕月乌：毕网，雨师，主网罗捕获。 */
    BI_WEST(
        starName = "毕", quadrant = Quadrant.WHITE_TIGER, order = 19,
        luminary = Luminary.MOON, beast = "乌", degree = 16f,
        component = "CdpFetchInterceptor", metric = "网络拦截数",
        gloss = "张网捕风，无有漏脱",
    ),

    /** 觜火猴：虎首虎口，三星紧密，主细微机巧。 */
    ZI(
        starName = "觜", quadrant = Quadrant.WHITE_TIGER, order = 20,
        luminary = Luminary.MARS, beast = "猴", degree = 2f,
        component = "GenericRecipeInstaller", metric = "脚本执行数",
        gloss = "细巧机变，见微知著",
    ),

    /** 参水猿：白虎之身，主杀伐、权衡、边事。 */
    SHEN(
        starName = "参", quadrant = Quadrant.WHITE_TIGER, order = 21,
        luminary = Luminary.MERCURY, beast = "猿", degree = 9f,
        component = "SandboxBoundary", metric = "越权拦截数",
        gloss = "镇守边界，权衡可否",
    ),

    // ── 南方朱雀七宿（一百一十二度）：表现与体验层 ────────────────────────

    /** 井木犴：朱雀之首，东井主水衡、泉源。 */
    JING(
        starName = "井", quadrant = Quadrant.VERMILION_BIRD, order = 22,
        luminary = Luminary.JUPITER, beast = "犴", degree = 33f,
        component = "Theme", metric = "主题切换数",
        gloss = "泉源所出，润泽全体",
    ),

    /** 鬼金羊：朱雀之目（舆鬼），主积聚。 */
    GUI(
        starName = "鬼", quadrant = Quadrant.VERMILION_BIRD, order = 23,
        luminary = Luminary.VENUS, beast = "羊", degree = 4f,
        component = "RuntimeComponents", metric = "组件复用率",
        gloss = "积聚器用，以备百工",
    ),

    /** 柳土獐：朱雀之口，主吐露。 */
    LIU(
        starName = "柳", quadrant = Quadrant.VERMILION_BIRD, order = 24,
        luminary = Luminary.SATURN, beast = "獐", degree = 15f,
        component = "MarkdownText", metric = "渲染帧耗时",
        gloss = "出口成章，吐露文辞",
    ),

    /** 星日马：朱雀之颈（七星），主引领方向。 */
    XING(
        starName = "星", quadrant = Quadrant.VERMILION_BIRD, order = 25,
        luminary = Luminary.SUN, beast = "马", degree = 7f,
        component = "Navigation3", metric = "路由跳转数",
        gloss = "引领方向，指引来处",
    ),

    /** 张月鹿：朱雀之嗉，主天厨、陈列宴飨 —— 摇光门后之器。 */
    ZHANG(
        starName = "张", quadrant = Quadrant.VERMILION_BIRD, order = 26,
        luminary = Luminary.MOON, beast = "鹿", degree = 18f,
        component = "SettingsStore", metric = "偏好读写数",
        gloss = "陈列百味，各取所需",
    ),

    /** 翼火蛇：朱雀之翼，二十二星，主铺张舒展。 */
    YI(
        starName = "翼", quadrant = Quadrant.VERMILION_BIRD, order = 27,
        luminary = Luminary.MARS, beast = "蛇", degree = 18f,
        component = "WorkflowEditor", metric = "画布节点数",
        gloss = "展翼铺陈，图绘全局",
    ),

    /** 轸水蚓：朱雀之尾，又名天车，主车驾承载 —— 天玑门后之器。 */
    ZHEN(
        starName = "轸", quadrant = Quadrant.VERMILION_BIRD, order = 28,
        luminary = Luminary.MERCURY, beast = "蚓", degree = 17f,
        component = "PtySession", metric = "会话存活数",
        gloss = "载具以行，通达无碍",
    ),
    ;

    /** 「七曜·禽」全称，如「角木蛟」。 */
    val fullName: String get() = "$starName${luminary.label}$beast"

    /** 「宿名（构件）」的指代式，用于帮助文案，如「奎宿（工具注册表）」。 */
    val reference: String get() = "${starName}宿（$component）"

    companion object {
        /** 按序次取宿（1..28），越界返回 null。 */
        fun ofOrder(order: Int): Mansion? = entries.firstOrNull { it.order == order }

        /** 按宿名取宿，用于提示词与配置解析。 */
        fun ofName(name: String): Mansion? = entries.firstOrNull { it.starName == name }

        /** 按构件名取宿，用于日志 tag 反查。 */
        fun ofComponent(component: String): Mansion? =
            entries.firstOrNull { it.component.equals(component, ignoreCase = true) }

        /** 周天距度总和：365¼ 度。 */
        val TOTAL_DEGREES: Float get() = entries.sumOf { it.degree.toDouble() }.toFloat()
    }
}

/**
 * 七曜 —— 二十八宿的七曜配属，木金土日月火水循环往复。
 *
 * 四象各七宿，每象之内七曜次序一致，故只需记一象便知其余。
 */
enum class Luminary(
    /** 曜名，如「木」。 */
    val label: String,
    /** 对应天体名。 */
    val body: String,
) {
    /** 岁星。 */
    JUPITER(label = "木", body = "岁星"),

    /** 太白。 */
    VENUS(label = "金", body = "太白"),

    /** 镇星（填星）。 */
    SATURN(label = "土", body = "镇星"),

    /** 日。 */
    SUN(label = "日", body = "太阳"),

    /** 月。 */
    MOON(label = "月", body = "太阴"),

    /** 荧惑。 */
    MARS(label = "火", body = "荧惑"),

    /** 辰星。 */
    MERCURY(label = "水", body = "辰星"),
}
