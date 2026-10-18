package top.wkbin.tianxuan.harness.projection

import kotlinx.serialization.json.Json
import top.wkbin.tianxuan.harness.HarnessMessage

/**
 * 投影初始化的不可变会话元数据（对齐 DSH SessionHeader 的最小投影面）。
 * 单元只读此快照，不依赖数据库实体——投影保持纯折叠（驱动逻辑与存储解耦）。
 */
data class ProjectionSessionMeta(
    /** 会话 ID。 */
    val id: String,
    /** Unix epoch 毫秒，会话创建时间。 */
    val createdAt: Long,
    /** 会话绝对工作目录（若有）。 */
    val cwd: String? = null,
    /** 分叉来源会话 ID（若有）。 */
    val parentSessionId: String? = null,
    /** 是否包含分叉继承的事件前缀。 */
    val isSeeded: Boolean = false,
    /** 会话起源分类（subagent 等）。 */
    val origin: String? = null,
    /** 委派深度。 */
    val delegationDepth: Int? = null,
)

/**
 * 状态驱动纯折叠单元（对齐 DSH session-projection/src/index.ts 的 ProjectionDefinition）。
 *
 * 框架在每个已提交的会话事件上驱动 [apply]；域仅持有计算逻辑，不持有订阅。
 * 所有函数必须是同步的（异步单元会撕裂载体的一致性切面）。
 *
 * @param K 投影键类型
 * @param S 状态类型
 */
interface ProjectionDefinition<K : Any, S> {
    /** 此单元拥有的投影键。 */
    val key: K

    /** 空日志及不可变会话元数据的初始状态。 */
    fun init(header: ProjectionSessionMeta, inheritedEventCount: Int): S

    /**
     * 纯转换：先前状态 + 一个已提交事件 → 下一状态。
     * 对不关心的事件必须返回同一状态引用（=== 产生零下游工作）。
     *
     * @param state 覆盖所有先前事件的状态
     * @param event 下一个已提交的会话事件
     * @return 下一状态（事件不属于此单元时为同一引用）
     */
    fun apply(state: S, event: HarnessMessage): S

    /**
     * 客户端视图（host-only 单元可省略）。
     * 活跃驱动保留最近两个原始结果用引用比较；对象值视图必须重用
     * 其引用以抑制内部状态变更的发布。
     */
    fun view(state: S): Any?
}

/**
 * 变更监听器：一个单元的原始 view 结果因引用不等而对一个会话改变。
 * [value] 是 schema 校验后的输出；[seq] 是发射时该单元的水位线。
 */
typealias ProjectionChangeListener = (value: Any?, seq: Long) -> Unit

/**
 * 会话投影注册表（对齐 DSH session-projection/src/index.ts 的 SessionProjectionRegistry）。
 *
 * 汇聚注册的投影单元、按会话驱动它们、向消费者暴露最新状态快照。
 * 每个投影单元是独立的纯折叠，注册表只负责订阅、每会话水位线缓存和变更通知。
 *
 * @param json JSON 序列化器（用于状态持久化缓存）
 */
class SessionProjectionRegistry(
    private val json: Json,
) {
    /** 每个会话的投影状态缓存。 */
    private class SessionProjections {
        /** 各单元的状态，key = 投影键的规范名。 */
        val states = mutableMapOf<String, Any>()
        /** 各单元的变更监听器。 */
        val listeners = mutableMapOf<String, MutableList<ProjectionChangeListener>>()
        /** 最后处理的会话事件 seq（--1 = 尚未处理）。 */
        var watermarkSeq: Long = -1
    }

    private val definitions = java.util.concurrent.ConcurrentHashMap<String, ProjectionDefinition<*, *>>()
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, SessionProjections>()

    /**
     * 注册一个投影单元。
     */
    fun register(definition: ProjectionDefinition<*, *>) {
        val key = definition.key.toString()
        require(!definitions.containsKey(key)) { "Projection key \"$key\" already registered" }
        definitions[key] = definition
    }

    /**
     * 对一个会话驱动所有注册的投影单元。
     *
     * @param sessionId 会话 ID
     * @param header 会话不可变元数据
     * @param events 按序提交的会话事件
     */
    fun drive(sessionId: String, header: ProjectionSessionMeta, events: List<HarnessMessage>) {
        val projections = sessions.getOrPut(sessionId) { SessionProjections() }

        for ((key, definition) in definitions) {
            val untypedDef = definition as ProjectionDefinition<Any, Any>
            val currentState = projections.states[key]
            val newState = if (currentState == null) {
                untypedDef.init(header, 0)
            } else {
                currentState
            }

            var state = newState
            for (event in events) {
                state = untypedDef.apply(state, event)
            }

            if (state !== currentState) {
                projections.states[key] = state
                val view = untypedDef.view(state)
                if (view != null) {
                    val listeners = projections.listeners[key]
                    if (listeners != null) {
                        for (listener in listeners) {
                            runCatching { listener(view, projections.watermarkSeq) }
                        }
                    }
                }
            }
        }
    }

    /**
     * 读取一个会话的投影状态快照。
     */
    fun <S> stateOf(sessionId: String, key: Any): S? {
        val projections = sessions[sessionId] ?: return null
        return projections.states[key.toString()] as? S
    }

    /**
     * 读取一个会话的投影客户端视图。
     */
    fun <V> viewOf(sessionId: String, key: Any): V? {
        val projections = sessions[sessionId] ?: return null
        val state = projections.states[key.toString()] ?: return null
        val definition = definitions[key.toString()] as? ProjectionDefinition<Any, Any> ?: return null
        return definition.view(state) as? V
    }

    /**
     * 订阅一个投影的变更。
     *
     * @return 取消订阅函数
     */
    fun onChange(sessionId: String, key: Any, listener: ProjectionChangeListener): () -> Unit {
        val projections = sessions.getOrPut(sessionId) { SessionProjections() }
        val listeners = projections.listeners.getOrPut(key.toString()) { mutableListOf() }
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * 删除一个会话的所有投影（会话删除时调用）。
     */
    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    /**
     * 清空所有投影（全局重置）。
     */
    fun clear() {
        sessions.clear()
    }
}