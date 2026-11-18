/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.corecontrol

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.util.Log
import androidx.preference.PreferenceManager
import com.xiaomi.settings.R
import java.io.File
import java.nio.file.Files

/** Shared sysfs helpers, presets and persistence for CPU Manager (little, big, GPU, cores). */
object CpuManagerUtils {

    private const val TAG = "CpuManagerUtils"
    private const val RESTORE_DELAY_MS = 20_000L
    private const val PRESET_KEY = "cpum_preset"
    private const val PRESET_CUSTOM = "custom"
    private const val PROP_MANUAL = "sys.xiaomiparts.perf_manual"

    const val NUM_CORES = 8
    const val CPU = "/sys/devices/system/cpu"
    const val GPU = "/sys/class/kgsl/kgsl-3d0/devfreq"

    /** One tunable block: a cpufreq policy or the GPU devfreq node. */
    class Domain(
        val key: String,
        val dir: String,
        val unit: Long, // divisor to convert node value to MHz
        val minName: String,
        val maxName: String,
        val govName: String,
        val freqsName: String,
        val govsName: String
    ) {
        fun node(name: String) = "$dir/$name"
        val minKey get() = "cpum_${key}_min"
        val maxKey get() = "cpum_${key}_max"
        val govKey get() = "cpum_${key}_gov"
    }

    private fun cpuDomain(key: String, cpu: Int) = Domain(
        key, "$CPU/cpu$cpu/cpufreq", 1000L,
        "scaling_min_freq", "scaling_max_freq", "scaling_governor",
        "scaling_available_frequencies", "scaling_available_governors"
    )

    val domains: List<Domain> = listOf(
        cpuDomain("little", 0),
        cpuDomain("big", 4),
        Domain(
            "gpu", GPU, 1_000_000L,
            "min_freq", "max_freq", "governor",
            "available_frequencies", "available_governors"
        )
    )

    // ---------------------------------------------------------- presets

    enum class Preset(val id: String, val labelRes: Int) {
        BATTERY("battery", R.string.cpu_manager_preset_battery),
        BALANCED("balanced", R.string.cpu_manager_preset_balanced),
        PERFORMANCE("performance", R.string.cpu_manager_preset_performance),
        DEFAULT("default", R.string.cpu_manager_preset_default);

        companion object {
            fun fromId(id: String?): Preset? = values().firstOrNull { it.id == id }
        }
    }

    /**
     * Position of min/max inside the sorted available-frequency list (0.0 = lowest,
     * 1.0 = highest). Starting values, not tuned: adjust after measuring on device.
     */
    private fun fractions(p: Preset, key: String): Pair<Double, Double> = when (p) {
        Preset.BALANCED, Preset.DEFAULT -> 0.0 to 1.0
        Preset.BATTERY -> when (key) {
            "little" -> 0.0 to 0.6
            else -> 0.0 to 0.5 // big, gpu
        }
        Preset.PERFORMANCE -> when (key) {
            "gpu" -> 0.33 to 1.0
            else -> 0.5 to 1.0 // little, big
        }
    }

    /** Governors a profile wants, best first. null = leave the governor alone (GPU). */
    private fun governorCandidates(p: Preset, d: Domain): List<String>? {
        if (d.key == "gpu") return null
        return when (p) {
            Preset.BATTERY -> listOf("powersave")
            Preset.BALANCED -> listOf("schedutil")
            Preset.PERFORMANCE -> listOf("performance")
            Preset.DEFAULT -> listOf("walt")
        }
    }

    /** Writes the first governor the kernel offers for this profile; returns its name. */
    private fun applyGovernor(d: Domain, p: Preset): String? {
        val wanted = governorCandidates(p, d) ?: return null
        val avail = readFile(d.node(d.govsName)).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val gov = wanted.firstOrNull { it in avail } ?: return null
        writeLocked(d.node(d.govName), gov)
        return gov
    }

    fun currentPresetId(ctx: Context): String? = prefs(ctx).getString(PRESET_KEY, null)

    fun presetLabel(ctx: Context): String {
        val id = currentPresetId(ctx)
        return Preset.fromId(id)?.let { ctx.getString(it.labelRes) }
            ?: ctx.getString(
                if (id == PRESET_CUSTOM) R.string.cpu_manager_preset_custom
                else R.string.cpu_manager_preset_default
            )
    }

    /** Tile order: Default/Balanced/Custom -> Performance -> Battery -> Balanced. */
    fun nextPreset(ctx: Context): Preset = when (Preset.fromId(currentPresetId(ctx))) {
        Preset.PERFORMANCE -> Preset.BATTERY
        Preset.BATTERY -> Preset.BALANCED
        else -> Preset.PERFORMANCE
    }

    fun applyPreset(ctx: Context, p: Preset) {
        ensureDefaults(ctx)
        val edit = prefs(ctx).edit()
        for (d in domains) {
            applyGovernor(d, p)?.let { edit.putString(d.govKey, it) }
            val freqs = readFreqs(d)
            if (freqs.isEmpty()) continue
            val (fMin, fMax) = fractions(p, d.key)
            val lo = freqs[Math.round((freqs.size - 1) * fMin).toInt()]
            val hi = freqs[Math.round((freqs.size - 1) * fMax).toInt()]
            applyMinMax(d, lo, hi)
            edit.putLong(d.minKey, lo).putLong(d.maxKey, hi)
        }
        edit.putString(PRESET_KEY, p.id).apply()
    }

    // ------------------------------------------------------------- save

    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)
    private fun coreKey(core: Int) = "cpum_core_$core"

    fun saveFreq(ctx: Context, d: Domain, isMin: Boolean, value: Long) {
        prefs(ctx).edit()
            .putLong(if (isMin) d.minKey else d.maxKey, value)
            .putString(PRESET_KEY, PRESET_CUSTOM)
            .apply()
    }

    fun saveGovernor(ctx: Context, d: Domain, gov: String) {
        prefs(ctx).edit()
            .putString(d.govKey, gov)
            .putString(PRESET_KEY, PRESET_CUSTOM)
            .apply()
    }

    fun saveCore(ctx: Context, core: Int, online: Boolean) {
        prefs(ctx).edit().putBoolean(coreKey(core), online).apply()
    }

    // ---------------------------------------------------------- restore

    /** Called from the boot receiver; waits so post_boot / perf HAL finish first. */
    fun scheduleRestore(context: Context) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            Thread { restore(app) }.start()
        }, RESTORE_DELAY_MS)
    }

    fun restore(context: Context) {
        val p = prefs(context)
        domains.forEach { restoreDomain(p, it) }
        for (i in 0 until NUM_CORES) {
            if (!p.contains(coreKey(i))) continue
            val online = p.getBoolean(coreKey(i), true)
            if (!online && !canOffline(i)) continue
            setCoreState(i, online)
        }
        Log.i(TAG, "CPU Manager settings restored")
    }

    private fun restoreDomain(p: SharedPreferences, d: Domain) {
        p.getString(d.govKey, null)?.let { writeLocked(d.node(d.govName), it) }

        val sMin = p.getLong(d.minKey, -1L)
        val sMax = p.getLong(d.maxKey, -1L)
        if (sMin < 0 && sMax < 0) return

        val curMin = readFile(d.node(d.minName)).toLongOrNull()
        val curMax = readFile(d.node(d.maxName)).toLongOrNull()
        val newMin = if (sMin >= 0) sMin else curMin ?: return
        val newMax = if (sMax >= 0) sMax else curMax ?: return
        if (newMin > newMax) return
        applyMinMax(d, newMin, newMax)
    }

    /** Writes min/max in an order that keeps min <= max at every step. */
    private fun applyMinMax(d: Domain, newMin: Long, newMax: Long) {
        val curMin = readFile(d.node(d.minName)).toLongOrNull()
        if (curMin != null && newMax < curMin) {
            writeLocked(d.node(d.minName), newMin.toString())
            writeLocked(d.node(d.maxName), newMax.toString())
        } else {
            writeLocked(d.node(d.maxName), newMax.toString())
            writeLocked(d.node(d.minName), newMin.toString())
        }
    }

    fun readFreqs(d: Domain): List<Long> =
        readFile(d.node(d.freqsName))
            .split(Regex("\\s+"))
            .mapNotNull { it.toLongOrNull() }
            .sorted()

    // ------------------------------------------------------------- core

    fun isCoreOnline(core: Int): Boolean {
        val path = "$CPU/cpu$core/online"
        return if (File(path).exists()) readFile(path) == "1" else File("$CPU/cpu$core").exists()
    }

    fun setCoreState(core: Int, online: Boolean) {
        writeFile("$CPU/cpu$core/online", if (online) "1" else "0")
    }

    fun canOffline(core: Int): Boolean {
        if (!File("$CPU/cpu$core/online").exists()) return false
        if (core in 0..3) {
            var onlineCount = 0
            for (i in 0..3) {
                if (i != core && isCoreOnline(i)) onlineCount++
            }
            return onlineCount >= 2
        }
        return true
    }

    @Volatile private var resetting = false

    fun enterManual() {
        if (resetting) return
        if (SystemProperties.get(PROP_MANUAL, "0") != "1") SystemProperties.set(PROP_MANUAL, "1")
    }

    fun leaveManual() = SystemProperties.set(PROP_MANUAL, "0")

    /** Enter manual mode (libperfmgr stops touching CPU/GPU), then write the node. */
    fun writeLocked(path: String, value: String) {
        enterManual()
        writeFile(path, value)
    }

    private fun defKey(d: Domain, n: String) = "cpum_def_${d.key}_$n"

    fun ensureDefaults(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean("cpum_def_v4", false)) return
        val e = p.edit()
        for (d in domains) {
            // live min can be a boost value (min == max) -> use the real lowest step
            val fr = readFreqs(d)
            e.putString(
                defKey(d, "min"),
                if (fr.isNotEmpty()) fr.first().toString() else readFile(d.node(d.minName))
            )
            e.putString(defKey(d, "max"), if (fr.isNotEmpty()) fr.last().toString() else readFile(d.node(d.maxName)))
            // stock CPU governor is walt; the live one may be changed by an init script
            val avail = readFile(d.node(d.govsName)).split(Regex("\\s+"))
            val live = readFile(d.node(d.govName))
            e.putString(defKey(d, "gov"), if (d.key != "gpu" && "walt" in avail) "walt" else live)
        }
        e.putBoolean("cpum_def_v4", true).apply()
    }

    /** Stock config back -> libperfmgr normal again; restore stock values, forget settings. */
    fun resetToDefault(ctx: Context) {
        resetting = true
        try {
            leaveManual()
            Thread.sleep(2500)
            val p = prefs(ctx)
            val e = p.edit()
            for (d in domains) {
                val gov = p.getString(defKey(d, "gov"), "").orEmpty()
                val min = p.getString(defKey(d, "min"), "")?.toLongOrNull()
                val max = p.getString(defKey(d, "max"), "")?.toLongOrNull()
                // walt if the kernel offers it, otherwise the stock governor saved earlier
                val dflt = applyGovernor(d, Preset.DEFAULT)
                if (dflt == null && gov.isNotEmpty()) writeLocked(d.node(d.govName), gov)
                if (min != null && max != null && min <= max) applyMinMax(d, min, max)
                e.remove(d.minKey).remove(d.maxKey).remove(d.govKey)
            }
            e.remove(PRESET_KEY).apply()
            Log.i(TAG, "CPU Manager reset")
        } finally {
            resetting = false
        }
    }

    // --------------------------------------------------------------- io

    fun readFile(path: String): String {
        return try {
            String(Files.readAllBytes(File(path).toPath())).trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $path", e)
            ""
        }
    }

    fun writeFile(path: String, value: String) {
        try {
            Files.write(File(path).toPath(), value.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $path", e)
        }
    }
}
