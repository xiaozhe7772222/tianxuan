package top.wkbin.tianxuan.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class CcAgentType(
    val id: String,
    val displayName: String,
    val defaultExecutable: String,
    val category: String = "CLI",
    val defaultLatestVersion: String = "",
) {
    CLAUDE_CODE("claude-code", "Claude Code", "claude", "CLI", "2.1.267"),
    CODEX("codex", "Codex", "codex", "CLI", "0.154.0"),
    GEMINI_CLI("gemini-cli", "Gemini CLI", "gemini", "CLI", "0.59.0"),
    GROK_BUILD("grok-build", "Grok Build", "grok", "CLI", "1.0.25"),
    OPENCODE("opencode", "OpenCode", "opencode", "CLI", "1.18.30"),
    OPENCLAW("openclaw", "OpenClaw", "openclaw", "Agent", "2026.9.3"),
    HERMES("hermes-agent", "Hermes Agent", "hermes", "Agent", "0.4.0"),
    PI("pi", "Pi", "pi", "Agent", "0.85.1");

    companion object {
        fun fromId(id: String): CcAgentType? = entries.firstOrNull { 
            it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) 
        }
    }
}

@Serializable
data class CcAgentState(
    val type: CcAgentType,
    val installed: Boolean = false,
    val currentVersion: String? = null,
    val latestVersion: String? = null,
    val availableVersions: List<String> = emptyList(),
    val activeProviderId: String? = null,
    val activeProviderName: String? = null,
    val activeModelName: String? = null,
    val isChecking: Boolean = false,
    val errorNotice: String? = null,
    val description: String = "",
    val running: Boolean = false,
    val servicePort: Int? = null,
    val webPath: String? = null,
) {
    val hasUpdate: Boolean
        get() = installed && !currentVersion.isNullOrBlank() && !latestVersion.isNullOrBlank() && currentVersion != latestVersion
}

@Serializable
data class CcProviderProfile(
    val id: String,
    val name: String,
    val protocol: String = "OPENAI", // "OPENAI" or "ANTHROPIC"
    val baseUrl: String = "",
    val apiKey: String = "",
    val maskedApiKey: String = "",
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val iconName: String? = null,
)

@Serializable
data class CcTokenUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    val totalCostEstimateCny: Double = 0.0,
    val requestCount: Long = 0,
)

@Serializable
data class CcSwitchDaemonStatus(
    val running: Boolean = false,
    val port: Int = 19870,
    val version: String = "1.0.0",
    val proxyEnabled: Boolean = true,
    val uptimeSeconds: Long = 0,
    val totalTokens: CcTokenUsage = CcTokenUsage(),
    val todayTokens: CcTokenUsage = CcTokenUsage(),
)
