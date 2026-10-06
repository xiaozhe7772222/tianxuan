package top.wkbin.tianxuan.core.common.logging

/** Minimal logging boundary that keeps the common module independent of security implementations. */
fun interface SensitiveDataRedactor {
    fun redact(value: String): String
}
