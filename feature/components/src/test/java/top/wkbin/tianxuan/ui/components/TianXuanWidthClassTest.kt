package top.wkbin.tianxuan.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 宽度分型与三栏判定测试。
 *
 * 这组判定直接决定「用底栏还是侧栏」「要不要第三栏」，一旦错判，
 * 平板上会出现内容被挤没或侧栏挤占过多的问题，且不会抛异常——
 * 只能靠断言锁住边界值。
 */
class TianXuanWidthClassTest {

    @Test
    fun `手机竖屏宽度判为紧凑型`() {
        assertEquals(TianXuanWidthClass.Compact, widthClassOf(320))
        assertEquals(TianXuanWidthClass.Compact, widthClassOf(411))
        assertEquals(TianXuanWidthClass.Compact, widthClassOf(599))
    }

    @Test
    fun `断点下界取到中等型`() {
        assertEquals(TianXuanWidthClass.Medium, widthClassOf(600))
    }

    @Test
    fun `11寸平板横屏落入中等型`() {
        // 1280x800 dp 的 11 寸平板
        assertEquals(TianXuanWidthClass.Medium, widthClassOf(800))
    }

    @Test
    fun `13寸平板横屏落入展开型`() {
        // 1366x1024 dp 的 13 寸平板
        assertEquals(TianXuanWidthClass.Expanded, widthClassOf(1024))
    }

    @Test
    fun `展开断点边界正确`() {
        assertEquals(TianXuanWidthClass.Medium, widthClassOf(839))
        assertEquals(TianXuanWidthClass.Expanded, widthClassOf(840))
    }

    @Test
    fun `只有紧凑型用底部栏`() {
        assertFalse(TianXuanWidthClass.Compact.usesPermanentNav)
        assertTrue(TianXuanWidthClass.Medium.usesPermanentNav)
        assertTrue(TianXuanWidthClass.Expanded.usesPermanentNav)
    }

    @Test
    fun `中等型侧栏不显示文字标签以留内容宽度`() {
        assertFalse(TianXuanWidthClass.Medium.showsNavLabels)
        assertTrue(TianXuanWidthClass.Expanded.showsNavLabels)
    }

    @Test
    fun `展开型且内容区足够时才展开第三栏`() {
        // 1024 - 240 = 784，可容纳两张 280dp
        assertTrue(shouldExpandListPane(TianXuanWidthClass.Expanded, 1024))
        // 1366 - 240 = 1126，更宽裕
        assertTrue(shouldExpandListPane(TianXuanWidthClass.Expanded, 1366))
    }

    @Test
    fun `展开型但内容区不足时不展开第三栏`() {
        // 阈值：可用宽度 - 侧栏 240 >= 两张列表 280，即可用宽度 >= 800。
        // 799 时内容区只剩 559dp，塞不下两张 280dp 列表。
        assertFalse(shouldExpandListPane(TianXuanWidthClass.Expanded, 799))
        assertFalse(shouldExpandListPane(TianXuanWidthClass.Expanded, 780))
    }

    @Test
    fun `第三栏判定的边界值两侧行为相反`() {
        // 800dp 是恰好的临界：内容区 560 == 两张列表宽度之和，应当展开。
        assertTrue(shouldExpandListPane(TianXuanWidthClass.Expanded, 800))
        assertFalse(shouldExpandListPane(TianXuanWidthClass.Expanded, 799))
    }

    @Test
    fun `紧凑与中等型一律不展开第三栏`() {
        assertFalse(shouldExpandListPane(TianXuanWidthClass.Compact, 599))
        assertFalse(shouldExpandListPane(TianXuanWidthClass.Medium, 839))
    }

    @Test
    fun `负宽度与零宽度退化为紧凑型而非抛异常`() {
        // 分屏拖拽过程中可用宽度可能瞬时为 0，此时应退化为最保守布局
        assertEquals(TianXuanWidthClass.Compact, widthClassOf(0))
        assertEquals(TianXuanWidthClass.Compact, widthClassOf(-1))
    }

    @Test
    fun `极大宽度不会溢出判定`() {
        assertEquals(TianXuanWidthClass.Expanded, widthClassOf(Int.MAX_VALUE))
    }
}