package top.wkbin.tianxuan.ui.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.Json
import top.wkbin.tianxuan.core.database.AiModelEntity
import top.wkbin.tianxuan.core.database.WorkflowScheduleEntity
import top.wkbin.tianxuan.core.model.workflow.WorkflowDefinition
import top.wkbin.tianxuan.core.model.workflow.WorkflowScheduleRepeat
import top.wkbin.tianxuan.harness.workflow.WorkflowScheduleRepository
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton

/** 重复方式的展示文案（列表与对话框共用）。 */
fun scheduleRepeatLabel(entity: WorkflowScheduleEntity): String = when (WorkflowScheduleRepeat.valueOf(entity.repeatType)) {
    WorkflowScheduleRepeat.DAILY -> "每天 ${"%02d".format(entity.hour ?: 0)}:${"%02d".format(entity.minute ?: 0)}"
    WorkflowScheduleRepeat.INTERVAL -> "每 ${entity.intervalMinutes ?: WorkflowScheduleRepository.MIN_INTERVAL_MINUTES} 分钟"
    WorkflowScheduleRepeat.ONCE -> "一次性 · ${
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entity.onceAtEpochMillis ?: 0L))
    }"
    WorkflowScheduleRepeat.WEEKLY -> "每周 ${entity.weekDays ?: "?"} ${"%02d".format(entity.hour ?: 0)}:${"%02d".format(entity.minute ?: 0)}"
    WorkflowScheduleRepeat.CRON -> "Cron: ${entity.cronExpression ?: "?"}"
}

/**
 * 定时计划新建/编辑对话框：选工作流 + 重复方式（每天 HH:mm / 每 N 分钟 / 一次性时刻）+
 * 变量（KEY=VALUE 行式）+ 工作区 + 可选模型。变量与时刻在保存前做格式校验；
 * 编辑时保留原 id 与启用状态，避免 upsert 产生孤儿行。
 */
@Composable
fun WorkflowScheduleEditDialog(
    definitions: List<WorkflowDefinition>,
    models: List<AiModelEntity>,
    existing: WorkflowScheduleEntity?,
    onDismiss: () -> Unit,
    onSave: (WorkflowScheduleEntity) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var workflowId by remember { mutableStateOf(existing?.workflowId ?: definitions.firstOrNull()?.id.orEmpty()) }
    var repeat by remember {
        mutableStateOf(existing?.let { WorkflowScheduleRepeat.valueOf(it.repeatType) } ?: WorkflowScheduleRepeat.DAILY)
    }
    var hour by remember { mutableStateOf((existing?.hour ?: 9).toString()) }
    var minute by remember { mutableStateOf((existing?.minute ?: 0).toString()) }
    var intervalMinutes by remember {
        mutableStateOf((existing?.intervalMinutes ?: 60).toString())
    }
    var onceAt by remember {
        mutableStateOf(
            existing?.onceAtEpochMillis?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
            }.orEmpty(),
        )
    }
    var weekDays by remember { mutableStateOf(existing?.weekDays ?: "1,2,3,4,5") }
    var cronExpression by remember { mutableStateOf(existing?.cronExpression ?: "0 9 * * 1-5") }
    var variablesText by remember { mutableStateOf(formatVariables(existing?.variablesJson)) }
    var workspacePath by remember {
        mutableStateOf(existing?.workspacePath?.ifBlank { "/workspace" } ?: "/workspace")
    }
    var modelId by remember { mutableStateOf(existing?.modelId.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建定时计划" else "编辑定时计划") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("计划名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SimplePickerField(
                    label = "目标工作流",
                    value = definitions.firstOrNull { it.id == workflowId }?.name ?: "请选择",
                    options = definitions.map { it.id to it.name },
                    onSelect = { workflowId = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = repeat == WorkflowScheduleRepeat.DAILY, onClick = { repeat = WorkflowScheduleRepeat.DAILY }, label = { Text("每天") })
                    FilterChip(selected = repeat == WorkflowScheduleRepeat.INTERVAL, onClick = { repeat = WorkflowScheduleRepeat.INTERVAL }, label = { Text("周期") })
                    FilterChip(selected = repeat == WorkflowScheduleRepeat.ONCE, onClick = { repeat = WorkflowScheduleRepeat.ONCE }, label = { Text("一次性") })
                    FilterChip(selected = repeat == WorkflowScheduleRepeat.WEEKLY, onClick = { repeat = WorkflowScheduleRepeat.WEEKLY }, label = { Text("每周") })
                    FilterChip(selected = repeat == WorkflowScheduleRepeat.CRON, onClick = { repeat = WorkflowScheduleRepeat.CRON }, label = { Text("Cron") })
                }
                when (repeat) {
                    WorkflowScheduleRepeat.DAILY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hour, onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                            label = { Text("时(0-23)") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = minute, onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                            label = { Text("分(0-59)") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                    }
                    WorkflowScheduleRepeat.INTERVAL -> OutlinedTextField(
                        value = intervalMinutes, onValueChange = { intervalMinutes = it.filter(Char::isDigit).take(4) },
                        label = { Text("间隔分钟（≥15）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    WorkflowScheduleRepeat.ONCE -> OutlinedTextField(
                        value = onceAt, onValueChange = { onceAt = it },
                        label = { Text("触发时间（yyyy-MM-dd HH:mm）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    WorkflowScheduleRepeat.WEEKLY -> Column {
                        OutlinedTextField(
                            value = weekDays, onValueChange = { weekDays = it.filter { c -> c.isDigit() || c == ',' } },
                            label = { Text("工作日（1=周一..7=周日，逗号分隔，如 1,3,5）") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = hour, onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                            label = { Text("时(0-23)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = minute, onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                            label = { Text("分(0-59)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    WorkflowScheduleRepeat.CRON -> OutlinedTextField(
                        value = cronExpression, onValueChange = { cronExpression = it },
                        label = { Text("Cron 表达式（分 时 日 月 周，如 0 9 * * 1-5）") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = variablesText,
                    onValueChange = { variablesText = it },
                    label = { Text("变量（每行 KEY=VALUE，可空）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize),
                )
                OutlinedTextField(
                    value = workspacePath,
                    onValueChange = { workspacePath = it },
                    label = { Text("工作区路径") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SimplePickerField(
                    label = "模型（可选，默认走全局）",
                    value = models.firstOrNull { it.id == modelId }?.name ?: "全局默认",
                    options = listOf("" to "全局默认") + models.map { it.id to it.name },
                    onSelect = { modelId = it },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = { RuntimeOutlinedButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = {
            RuntimeButton(
                onClick = {
                    val parsed = validateAndBuild(
                        existing = existing,
                        name = name, workflowId = workflowId, definitions = definitions,
                        repeat = repeat, hour = hour, minute = minute,
                        intervalMinutes = intervalMinutes, onceAt = onceAt,
                        weekDays = weekDays, cronExpression = cronExpression,
                        variablesText = variablesText, workspacePath = workspacePath, modelId = modelId,
                    )
                    parsed.fold(
                        onSuccess = { onSave(it) },
                        onFailure = { error = it.message },
                    )
                },
            ) { Text("保存") }
        },
    )
}

private fun validateAndBuild(
    existing: WorkflowScheduleEntity?,
    name: String,
    workflowId: String,
    definitions: List<WorkflowDefinition>,
    repeat: WorkflowScheduleRepeat,
    hour: String,
    minute: String,
    intervalMinutes: String,
    onceAt: String,
    weekDays: String,
    cronExpression: String,
    variablesText: String,
    workspacePath: String,
    modelId: String,
): Result<WorkflowScheduleEntity> {
    if (name.isBlank()) return Result.failure(IllegalArgumentException("请填写计划名称"))
    if (definitions.none { it.id == workflowId }) return Result.failure(IllegalArgumentException("请选择目标工作流"))
    val variables = parseVariables(variablesText).fold(
        onSuccess = { it },
        onFailure = { return Result.failure(it) },
    )
    val now = System.currentTimeMillis()
    return when (repeat) {
        WorkflowScheduleRepeat.DAILY -> {
            val h = hour.toIntOrNull() ?: return Result.failure(IllegalArgumentException("时必须是数字"))
            val m = minute.toIntOrNull() ?: return Result.failure(IllegalArgumentException("分必须是数字"))
            if (h !in 0..23 || m !in 0..59) return Result.failure(IllegalArgumentException("时刻超出范围"))
            Result.success(buildEntity(existing, name, workflowId, repeat, h, m, null, null, variables, workspacePath, modelId, now))
        }
        WorkflowScheduleRepeat.INTERVAL -> {
            val minutes = intervalMinutes.toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("间隔必须是数字"))
            if (minutes < WorkflowScheduleRepository.MIN_INTERVAL_MINUTES) {
                return Result.failure(IllegalArgumentException("间隔至少 ${WorkflowScheduleRepository.MIN_INTERVAL_MINUTES} 分钟"))
            }
            Result.success(buildEntity(existing, name, workflowId, repeat, null, null, minutes, null, variables, workspacePath, modelId, now))
        }
        WorkflowScheduleRepeat.ONCE -> {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }
            val target = try {
                parser.parse(onceAt.trim())?.time
            } catch (_: ParseException) {
                null
            } ?: return Result.failure(IllegalArgumentException("触发时间格式应为 yyyy-MM-dd HH:mm"))
            if (target <= now) return Result.failure(IllegalArgumentException("触发时间必须晚于当前时刻"))
            Result.success(buildEntity(existing, name, workflowId, repeat, null, null, null, target, variables, workspacePath, modelId, now))
        }
        WorkflowScheduleRepeat.WEEKLY -> {
            val h = hour.toIntOrNull() ?: return Result.failure(IllegalArgumentException("时必须是数字"))
            val m = minute.toIntOrNull() ?: return Result.failure(IllegalArgumentException("分必须是数字"))
            if (h !in 0..23 || m !in 0..59) return Result.failure(IllegalArgumentException("时刻超出范围"))
            val days = weekDays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }
            if (days.isEmpty()) return Result.failure(IllegalArgumentException("工作日至少一个（1=周一..7=周日，逗号分隔）"))
            Result.success(buildEntity(existing, name, workflowId, repeat, h, m, null, null, variables, workspacePath, modelId, now, weekDays = days.joinToString(","), cronExpression = null))
        }
        WorkflowScheduleRepeat.CRON -> {
            val expr = cronExpression.trim()
            if (expr.split(Regex("\\s+")).filter { it.isNotBlank() }.size != 5) {
                return Result.failure(IllegalArgumentException("Cron 表达式必须是五字段（分 时 日 月 周），如 0 9 * * 1-5"))
            }
            val next = top.wkbin.tianxuan.core.model.workflow.CronScheduler.nextOccurrence(expr)
                ?: return Result.failure(IllegalArgumentException("Cron 表达式无未来匹配"))
            Result.success(buildEntity(existing, name, workflowId, repeat, null, null, null, null, variables, workspacePath, modelId, now, weekDays = null, cronExpression = expr))
        }
    }
}

private fun buildEntity(
    existing: WorkflowScheduleEntity?,
    name: String,
    workflowId: String,
    repeat: WorkflowScheduleRepeat,
    hour: Int?,
    minute: Int?,
    intervalMinutes: Int?,
    onceAtEpochMillis: Long?,
    variables: Map<String, String>,
    workspacePath: String,
    modelId: String,
    now: Long,
    weekDays: String? = null,
    cronExpression: String? = null,
): WorkflowScheduleEntity = WorkflowScheduleEntity(
    id = existing?.id ?: "sched_${UUID.randomUUID().toString().take(12)}",
    workflowId = workflowId,
    name = name,
    enabled = existing?.enabled ?: true,
    repeatType = repeat.name,
    hour = hour,
    minute = minute,
    intervalMinutes = intervalMinutes,
    onceAtEpochMillis = onceAtEpochMillis,
    weekDays = weekDays,
    cronExpression = cronExpression,
    variablesJson = Json.encodeToString(variables),
    workspacePath = workspacePath,
    modelId = modelId.ifBlank { null },
    modelVariant = existing?.modelVariant,
    lastExecutionId = existing?.lastExecutionId,
    lastRunAt = existing?.lastRunAt,
    nextRunAt = existing?.nextRunAt,
    createdAt = existing?.createdAt ?: now,
)

private fun parseVariables(text: String): Result<Map<String, String>> {
    val map = linkedMapOf<String, String>()
    text.lines().forEach { raw ->
        val line = raw.trim().takeIf { it.isNotEmpty() } ?: return@forEach
        val index = line.indexOf('=')
        if (index <= 0) return Result.failure(IllegalArgumentException("变量行格式错误：$line"))
        map[line.substring(0, index).trim()] = line.substring(index + 1).trim()
    }
    return Result.success(map)
}

private fun formatVariables(variablesJson: String?): String {
    if (variablesJson.isNullOrBlank()) return ""
    return runCatching {
        Json.decodeFromString<Map<String, String>>(variablesJson)
            .entries.joinToString("\n") { "${it.key}=${it.value}" }
    }.getOrDefault("")
}

/** 轻量下拉选择：OutlinedTextField 只读外观 + DropdownMenu，不依赖 ExposedDropdownMenuBox 的版本敏感 API。 */
@Composable
private fun SimplePickerField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // readOnly TextField 会消费触摸；用透明覆盖层接管点击事件打开菜单
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .heightIn(max = 260.dp),
        ) {
            options.forEach { (id, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
