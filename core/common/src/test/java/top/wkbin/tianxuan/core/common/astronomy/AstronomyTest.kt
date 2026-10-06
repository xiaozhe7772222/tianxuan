package top.wkbin.tianxuan.core.common.astronomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 星象体系数据不变量。
 *
 * 这些常量会被日志 tag、诊断面板与提示词引用，一旦写错就会静默误导模型与用户，
 * 因此以测试钉死：宿数、序次、四象分域、七曜循环、距度总和（合周天）、
 * 构件与指标的一一对应，都必须与古籍所载一致。
 */
class AstronomyTest {

    @Test
    fun twentyEightMansionsAreComplete() {
        assertEquals(28, Mansion.entries.size)
        assertEquals((1..28).toList(), Mansion.entries.map { it.order })
    }

    @Test
    fun eachQuadrantHoldsSevenMansions() {
        assertEquals(4, Quadrant.entries.size)
        Quadrant.entries.forEach { quadrant ->
            assertEquals("${quadrant.label} 应辖七宿", 7, quadrant.mansions.size)
        }
        // 四象合计即为二十八宿
        assertEquals(28, Quadrant.entries.sumOf { it.mansions.size })
    }

    /** 《淮南子·天文训》「星分度」：四象合计 365 度，箕宿带四分一，合周天 365¼ 度。 */
    @Test
    fun degreesSumToOneFullCircle() {
        val eastern = Quadrant.AZURE_DRAGON.mansions.sumOf { it.degree.toDouble() }
        val northern = Quadrant.BLACK_TORTOISE.mansions.sumOf { it.degree.toDouble() }
        val western = Quadrant.WHITE_TIGER.mansions.sumOf { it.degree.toDouble() }
        val southern = Quadrant.VERMILION_BIRD.mansions.sumOf { it.degree.toDouble() }

        // 原文作「东方七十五度」，而箕宿带四分一，故实为 75¼；其余三象皆为整数。
        assertEquals(75.25, eastern, 1e-6)
        assertEquals(98.0, northern, 1e-6)
        assertEquals(80.0, western, 1e-6)
        assertEquals(112.0, southern, 1e-6)
        assertEquals(365.25, Mansion.TOTAL_DEGREES.toDouble(), 1e-6)
    }

    @Test
    fun jiMansionCarriesTheExtraQuarterDegree() {
        assertEquals(11.25f, Mansion.JI.degree, 1e-6f)
    }

    @Test
    fun jingIsWidestAndZiIsNarrowest() {
        assertEquals(33f, Mansion.JING.degree, 1e-6f)
        assertEquals(2f, Mansion.ZI.degree, 1e-6f)
    }

    /** 七曜按木金土日月火水循环，每象之内次序一致。 */
    @Test
    fun luminariesCycleInCanonicalOrder() {
        val expected = listOf(
            Luminary.JUPITER, Luminary.VENUS, Luminary.SATURN, Luminary.SUN,
            Luminary.MOON, Luminary.MARS, Luminary.MERCURY,
        )
        Quadrant.entries.forEach { quadrant ->
            val actual = quadrant.mansions.sortedBy { it.order }.map { it.luminary }
            assertEquals("${quadrant.label} 七曜次序不符", expected, actual)
        }
    }

    /** 每宿绑定唯一构件与唯一指标，否则诊断面板分區会互相串扰。 */
    @Test
    fun componentsAndMetricsAreOneToOne() {
        assertEquals(28, Mansion.entries.map { it.component }.toSet().size)
        assertEquals(28, Mansion.entries.map { it.metric }.toSet().size)
    }

    @Test
    fun ofComponentResolvesBackToMansion() {
        Mansion.entries.forEach { mansion ->
            assertEquals(mansion, Mansion.ofComponent(mansion.component))
        }
        assertEquals(null, Mansion.ofComponent("NoSuchComponent"))
    }

    @Test
    fun fullNamesFollowLuminaryBeastPattern() {
        assertEquals("角木蛟", Mansion.JIAO.fullName)
        assertEquals("参水猿", Mansion.SHEN.fullName)
        assertEquals("轸水蚓", Mansion.ZHEN.fullName)
    }

    /** 门与器分属两层：北斗七门不与二十八宿构件同名。 */
    @Test
    fun gatesDoNotCollideWithMansionComponents() {
        val gates = BeiDou.entries.map { it.gateKey }.toSet()
        val components = Mansion.entries.map { it.component }.toSet()
        assertTrue(gates.intersect(components).isEmpty())
    }

    @Test
    fun beiDouSplitsIntoKuiAndBiao() {
        assertEquals(7, BeiDou.entries.size)
        assertEquals((1..7).toList(), BeiDou.entries.map { it.order })
        assertEquals(4, BeiDou.entries.count { it.part == DipperPart.KUI })
        assertEquals(3, BeiDou.entries.count { it.part == DipperPart.BIAO })
        assertEquals(
            listOf("天枢", "天璇", "天玑", "天权"),
            BeiDou.entries.filter { it.part == DipperPart.KUI }.map { it.starName },
        )
        assertEquals(
            listOf("玉衡", "开阳", "摇光"),
            BeiDou.entries.filter { it.part == DipperPart.BIAO }.map { it.starName },
        )
    }

    /** 玉衡（ε Alioth）为北斗最亮之星，取义不可写错到别星。 */
    @Test
    fun gateLookupsAreConsistent() {
        assertEquals(BeiDou.TIAN_SHU, BeiDou.ofGateKey("agent"))
        assertEquals(BeiDou.TIAN_XUAN, BeiDou.ofGateKey("workspace"))
        assertEquals(BeiDou.TIAN_JI, BeiDou.ofGateKey("terminal"))
        assertEquals(BeiDou.TIAN_QUAN, BeiDou.ofGateKey("workflow"))
        assertEquals(BeiDou.YU_HENG, BeiDou.ofGateKey("git"))
        assertEquals(BeiDou.KAI_YANG, BeiDou.ofGateKey("browser"))
        assertEquals(BeiDou.YAO_GUANG, BeiDou.ofGateKey("settings"))
        assertEquals(BeiDou.YU_HENG, BeiDou.ofOrder(5))
        assertNotNull(BeiDou.ofStarName("天枢"))
    }

    @Test
    fun enclosuresCoverThreeMemoryLayers() {
        assertEquals(3, Enclosure.entries.size)
        assertEquals(3, Enclosure.entries.map { it.memoryLayer }.toSet().size)
        assertEquals(Enclosure.ZI_WEI, Enclosure.ofMemoryLayer("核心记忆"))
        assertEquals(Enclosure.TAI_WEI, Enclosure.ofMemoryLayer("工作记忆"))
        assertEquals(Enclosure.TIAN_SHI, Enclosure.ofMemoryLayer("检索记忆"))
        // 紫微垣为天帝居所，永久驻留，不参与归档与按需进出
        assertTrue(Enclosure.ZI_WEI.retention.contains("永久"))
    }

    @Test
    fun mansionLookupByNameResolves() {
        assertEquals(Mansion.DOU, Mansion.ofName("斗"))
        assertEquals(Mansion.ofOrder(1), Mansion.ofName("角"))
        assertEquals(null, Mansion.ofName("不存在"))
    }
}