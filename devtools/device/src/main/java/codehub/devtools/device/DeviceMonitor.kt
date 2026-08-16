package codehub.devtools.device

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import codehub.devtools.memory.MemoryMonitor
import codehub.devtools.memory.MemorySnapshot
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val sdkInt: Int,
    val release: String,
    val abis: List<String>,
    val memory: MemorySnapshot,
    val storage: StorageSnapshot,
    val battery: BatterySnapshot,
    val thermalState: String,
    val displayInfo: DisplayInfo
)

@Serializable
data class StorageSnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    val blockedBytes: Long,
    val rootPath: String
)

@Serializable
data class BatterySnapshot(
    val level: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Int,
    val voltageMv: Int,
    val technology: String?
)

@Serializable
data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val scaledDensity: Float
)

@Singleton
class DeviceMonitor @Inject constructor(
    private val context: Context,
    private val memoryMonitor: MemoryMonitor
) {
    fun snapshot(): DeviceSnapshot {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val storagePath = context.filesDir.absolutePath
        val stat = StatFs(storagePath)

        val displayMetrics = context.resources.displayMetrics
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            deviceName = Build.DEVICE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE ?: "?",
            abis = Build.SUPPORTED_ABIS.toList(),
            memory = memoryMonitor.snapshot(),
            storage = StorageSnapshot(
                totalBytes = stat.totalBytes,
                availableBytes = stat.availableBytes,
                blockedBytes = stat.blockSizeLong,
                rootPath = storagePath
            ),
            battery = BatterySnapshot(
                level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                isCharging = bm.isCharging,
                temperatureCelsius = 0,
                voltageMv = 0,
                technology = null
            ),
            thermalState = thermalStateName(pm.currentThermalStatus),
            displayInfo = DisplayInfo(
                widthPx = displayMetrics.widthPixels,
                heightPx = displayMetrics.heightPixels,
                densityDpi = displayMetrics.densityDpi,
                refreshRateHz = displayMetrics.xdpi / 160f,
                scaledDensity = displayMetrics.scaledDensity
            )
        )
    }

    private fun thermalStateName(state: Int): String = when (state) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN"
    }
}
