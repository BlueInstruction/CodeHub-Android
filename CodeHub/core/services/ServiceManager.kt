package com.codehub.core.services

data class ServiceStatus(
    val name: String,
    val state: String,
    val healthCheck: String = "OK",
    val lastChecked: Long = System.currentTimeMillis()
)

interface ServiceManager {
    fun startService(name: String): ServiceStatus
    fun stopService(name: String): ServiceStatus
    fun restartService(name: String): ServiceStatus
    fun getStatus(name: String): ServiceStatus
    fun monitorService(name: String): Unit
}