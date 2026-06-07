package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.os.Build
import timber.log.Timber

object DeviceUtil {
    val isMiui by lazy {
        getSystemProperty("ro.miui.ui.version.name")?.isNotEmpty() ?: false
    }

    /**
     * Detect HyperOS (Xiaomi's newer OS replacing MIUI).
     * HyperOS sets ro.miui.ui.version.name to empty but has "ro.build.version.hyperos" or
     * manufacturer is Xiaomi with Android 14+ and no MIUI version.
     */
    val isHyperOS by lazy {
        if (isMiui) return@lazy false
        // HyperOS detection: Xiaomi/Poco/Redmi device with Android 14+ and no MIUI version
        val isXiaomi = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("redmi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("poco", ignoreCase = true)
        if (!isXiaomi) return@lazy false
        // Check for HyperOS-specific property
        val hyperOsVersion = getSystemProperty("ro.build.version.hyperos")
        if (hyperOsVersion?.isNotEmpty() == true) return@lazy true
        // Fallback: Xiaomi device with Android 14+ and no MIUI = likely HyperOS
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    /** True if device is either MIUI or HyperOS */
    val isXiaomiOrHyperOS by lazy { isMiui || isHyperOS }

    /**
     * Extracts the MIUI major version code from a string like "V12.5.3.0.QFGMIXM".
     *
     * @return MIUI major version code (e.g., 13) or null if can't be parsed.
     */
    val miuiMajorVersion by lazy {
        if (!isMiui) return@lazy null

        Build.VERSION.INCREMENTAL
            .substringBefore('.')
            .trimStart('V')
            .toIntOrNull()
    }

    @SuppressLint("PrivateApi")
    fun isMiuiOptimizationDisabled(): Boolean {
        val sysProp = getSystemProperty("persist.sys.miui_optimization")
        if (sysProp == "0" || sysProp == "false") {
            return true
        }

        return try {
            Class
                .forName("android.miui.AppOpsUtils")
                .getDeclaredMethod("isXOptMode")
                .invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    val isSamsung by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    val oneUiVersion by lazy {
        try {
            val semPlatformIntField = Build.VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT")
            val version = semPlatformIntField.getInt(null) - 90000
            if (version < 0) {
                1.0
            } else {
                ((version / 10000).toString() + "." + version % 10000 / 100).toDouble()
            }
        } catch (e: Exception) {
            null
        }
    }

    val invalidDefaultBrowsers =
        listOf(
            "android",
            "com.huawei.android.internal.app",
            "com.zui.resolver",
        )

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String?): String? =
        try {
            Class
                .forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (e: Exception) {
            Timber.w(e, "Unable to use SystemProperties.get")
            null
        }
}
