package com.codehub.core.diagnostics

enum class EventType {
    RUNTIME_INITIALIZATION,
    BUILD,
    PROCESS_FAILURE,
    MISSING_DEPENDENCY,
    PERMISSION,
    TERMINAL_FAILURE,
    CRASH,
    VULKAN_INITIALIZATION,
    AI_BACKEND_FAILURE,
    SERVICE_FAILURE
}

enum class Status {
    SUCCESS,
    SKIPPED,
    WARNING,
    ERROR,
    FAILURE
}

data class DiagnosticEvent(
    val event: String,
    val type: EventType,
    val status: Status,
    val reason: String? = null,
    val details: Map<String, String> = mapOf(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toHumanReadable(): String {
        val sb = StringBuilder()
        sb.append("[${timestamp}] ").append(type.name).append(": ").append(event)
        if (status != Status.SUCCESS) {
            sb.append(" - ").append(status.name)
        }
        if (reason != null) {
            sb.append(" (Reason: ").append(reason).append(")")
        }
        if (details.isNotEmpty()) {
            sb.append(" Details: ").append(details)
        }
        return sb.toString()
    }
}