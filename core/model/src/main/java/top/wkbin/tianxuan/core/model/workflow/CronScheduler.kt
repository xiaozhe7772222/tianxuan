package top.wkbin.tianxuan.core.model.workflow

import java.util.Calendar

/**
 * Cron 五字段时间计算器（对齐 DeepSeek Harness schedule/domain.ts 的 cron 解析）。
 *
 * 支持 Vixie cron 五字段：Minute(0-59) Hour(0-23) DayOfMonth(1-31) Month(1-12) DayOfWeek(0-7, 0/7=周日)
 * 每字段支持：星号（任意）、步进表达式（如星号/5 表示每 5 步）、a-b（范围）、a-b/n（范围每 n 步）、逗号列表。
 * DOM/DOW 语义：两字段都受限时 OR，任一为星号则 AND（严格 Vixie 语义）。
 */
object CronScheduler {

    /** 从当前时间起，下一次满足 cron 表达式的时刻（epoch millis）。无未来匹配时返回 null。 */
    fun nextOccurrence(cronExpression: String, fromMillis: Long = System.currentTimeMillis()): Long? {
        val fields = parseCron(cronExpression) ?: return null
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        // 从下一分钟开始找
        cal.add(Calendar.MINUTE, 1)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // 400 年地平线防无限扫描
        val horizon = cal.clone() as Calendar
        horizon.add(Calendar.YEAR, 400)
        val horizonMillis = horizon.timeInMillis
        var probe = cal.timeInMillis
        while (probe < horizonMillis) {
            if (matches(fields, probe)) return probe
            probe += 60_000L
        }
        return null
    }

    private data class CronFields(
        val minutes: Set<Int>?,
        val hours: Set<Int>?,
        val dayOfMonth: Set<Int>?,
        val months: Set<Int>?,
        val dayOfWeek: Set<Int>?,
    )

    private fun parseCron(expr: String): CronFields? {
        val parts = expr.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size != 5) return null
        return try {
            CronFields(
                minutes = parseField(parts[0], 0, 59),
                hours = parseField(parts[1], 0, 23),
                dayOfMonth = parseField(parts[2], 1, 31),
                months = parseField(parts[3], 1, 12),
                dayOfWeek = parseField(parts[4], 0, 7),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 解析单个 cron 字段为匹配值集合。null = "*"（任意）。 */
    private fun parseField(field: String, min: Int, max: Int): Set<Int>? {
        if (field == "*") return null
        val result = mutableSetOf<Int>()
        for (part in field.split(',')) {
            val stepMatch = Regex("""^(\*|\d+-\d+|\d+)/(\d+)$""").matchEntire(part)
            if (stepMatch != null) {
                val rangePart = stepMatch.groupValues[1]
                val step = stepMatch.groupValues[2].toInt()
                val (start, end) = if (rangePart == "*") min to max
                else rangePart.split('-').let { it[0].toInt() to (it.getOrNull(1)?.toInt() ?: max) }
                var i = start
                while (i <= end) {
                    result += i
                    i += step
                }
            } else if (part.contains('-')) {
                val (start, end) = part.split('-').map { it.toInt() }
                for (i in start..end) result += i
            } else {
                result += part.toInt()
            }
        }
        return result.filter { it in min..max }.takeIf { it.isNotEmpty() }?.toSet()
    }

    private fun matches(fields: CronFields, millis: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val minute = cal.get(Calendar.MINUTE)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1  // Calendar 周日=1 → 转为 0
        val isSunday = dow == 0
        val dowCron = if (isSunday) 7 else dow  // 周日按 7 参与匹配
        val minutes = fields.minutes
        val hours = fields.hours
        val months = fields.months
        val dayOfMonth = fields.dayOfMonth
        val dayOfWeek = fields.dayOfWeek
        if (minutes != null && minute !in minutes) return false
        if (hours != null && hour !in hours) return false
        if (months != null && month !in months) return false
        // DOW 的 0 与 7 都表示周日：字段写 "0" 或 "7" 都必须能命中周日。
        // 早先只把 dow 归一成 7，导致写法 "0"（Vixie 标准写法）永远匹配不到周日。
        val dowMatches = dayOfWeek != null && (dowCron in dayOfWeek || (isSunday && 0 in dayOfWeek))
        // DOM/DOW 语义：两字段都受限时 OR，任一为 * 则 AND
        return when {
            dayOfMonth != null && dayOfWeek != null -> dom in dayOfMonth || dowMatches
            dayOfMonth != null -> dom in dayOfMonth
            dayOfWeek != null -> dowMatches
            else -> true
        }
    }
}
