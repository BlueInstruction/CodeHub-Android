package com.codehub.core.permissions

enum class PermissionLevel {
    READ_ONLY,
    WORKSPACE_WRITE,
    BUILD,
    GIT_WRITE,
    FULL_AUTONOMY
}

data class PermissionRequest(
    val id: String,
    val permission: PermissionLevel,
    val resource: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

interface PermissionManager {
    fun checkPermission(level: PermissionLevel, resource: String): Boolean
    fun requestPermission(request: PermissionRequest): Boolean
    fun revokePermission(id: String): Boolean
    fun getCurrentPermissions(): List<PermissionLevel>
}