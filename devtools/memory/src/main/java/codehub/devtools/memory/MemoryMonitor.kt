package codehub.devtools.memory

import android.app.ActivityManager
import android.content.Context
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MemorySnapshot(
    val totalMemKb: Long,
    val availableMemKb: Long,
    val thresholdKb: Long,
    val lowMemory: Boolean,
    val totalSwapKb: Long,
    val availableSwapKb: Long,
    val nativeHeapFreeKb: Long,
    val nativeHeapTotalKb: Long,
    val runtimeFreeKb: Long,
    val runtimeTotalKb: Long,
    val runtimeMaxKb: Long
)

@Singleton
class MemoryMonitor @Inject constructor(
    private val context: Context
) {
    fun snapshot(): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(
            totalMemKb = info.totalMem / 1024,
            availableMemKb = info.availMem / 1024,
            thresholdKb = info.threshold / 1024,
            lowMemory = info.lowMemory,
            totalSwapKb = info.totalSwapMem / 1024,
            availableSwapKb = info.swapFreeMem / 1024,
            nativeHeapFreeKb = android.os.Debug.getNativeHeapFreeSize() / 1024,
            nativeHeapTotalKb = android.os.Debug.getNativeHeapAllocatedSize() / 1024,
            runtimeFreeKb = runtime.freeMemory() / 1024,
            runtimeTotalKb = runtime.totalMemory() / 1024,
            runtimeMaxKb = runtime.maxMemory() / 1024
        )
    }
}
