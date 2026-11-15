/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.corecontrol

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
import com.xiaomi.settings.R
import java.io.File
import java.nio.file.Files

/** CPU Manager: core on/off + min/max freq slider + governor for little, big and GPU. */
class CoreControlFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "CoreControlFragment"
        private const val NUM_CORES = 8
        private const val CPU = "/sys/devices/system/cpu"
        private const val GPU = "/sys/class/kgsl/kgsl-3d0/devfreq"
    }

    /** One tunable block: a cpufreq policy or the GPU devfreq node. */
    private class Domain(
        val key: String,
        val dir: String,
        val unit: Long, // divisor to convert node value to MHz
        val minName: String,
        val maxName: String,
        val govName: String,
        val freqsName: String,
        val govsName: String
    ) {
        var freqs: List<Long> = emptyList() // ascending, in node units
        var minPref: SliderPreference? = null
        var maxPref: SliderPreference? = null
        var govPref: ListPreference? = null
        fun node(name: String) = "$dir/$name"
    }

    private val mCorePrefs = arrayOfNulls<SwitchPreferenceCompat>(NUM_CORES)

    private val domains = listOf(
        cpuDomain("little", 0),
        cpuDomain("big", 4),
        Domain(
            "gpu", GPU, 1_000_000L,
            "min_freq", "max_freq", "governor",
            "available_frequencies", "available_governors"
        )
    )

    private fun cpuDomain(key: String, cpu: Int) = Domain(
        key, "$CPU/cpu$cpu/cpufreq", 1000L,
        "scaling_min_freq", "scaling_max_freq", "scaling_governor",
        "scaling_available_frequencies", "scaling_available_governors"
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.core_control_settings, rootKey)

        for (i in 0 until NUM_CORES) {
            mCorePrefs[i] = findPreference("core_$i")
            mCorePrefs[i]?.apply {
                onPreferenceChangeListener = this@CoreControlFragment
                isChecked = isCoreOnline(i)
            }
        }

        domains.forEach { setupDomain(it) }
    }

    override fun onResume() {
        super.onResume()
        for (i in 0 until NUM_CORES) mCorePrefs[i]?.isChecked = isCoreOnline(i)
        domains.forEach { refreshDomain(it) }
    }

    // ---------------------------------------------------------------- domain

    private fun setupDomain(d: Domain) {
        d.minPref = findPreference("${d.key}_min_freq")
        d.maxPref = findPreference("${d.key}_max_freq")
        d.govPref = findPreference("${d.key}_governor")

        d.freqs = readFile(d.node(d.freqsName))
            .split(Regex("\\s+"))
            .mapNotNull { it.toLongOrNull() }
            .sorted()

        if (d.freqs.isEmpty()) {
            d.minPref?.apply { isEnabled = false; summary = getString(R.string.cpu_manager_unsupported) }
            d.maxPref?.apply { isEnabled = false; summary = getString(R.string.cpu_manager_unsupported) }
        } else {
            listOf(d.minPref, d.maxPref).forEach {
                it?.apply {
                    min = 0
                    max = d.freqs.size - 1
                    onPreferenceChangeListener = this@CoreControlFragment
                }
            }
        }

        val govs = readFile(d.node(d.govsName))
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (govs.isEmpty()) {
            d.govPref?.apply { isEnabled = false; summary = getString(R.string.cpu_manager_unsupported) }
        } else {
            d.govPref?.apply {
                entries = govs.toTypedArray()
                entryValues = govs.toTypedArray()
                onPreferenceChangeListener = this@CoreControlFragment
            }
        }
        refreshDomain(d)
    }

    private fun refreshDomain(d: Domain) {
        if (d.freqs.isNotEmpty()) {
            val curMin = readFile(d.node(d.minName)).toLongOrNull()
            val curMax = readFile(d.node(d.maxName)).toLongOrNull()
            if (curMin != null) d.minPref?.apply { value = nearestIndex(d, curMin); summary = mhz(d, curMin) }
            if (curMax != null) d.maxPref?.apply { value = nearestIndex(d, curMax); summary = mhz(d, curMax) }
        }
        val gov = readFile(d.node(d.govName))
        if (gov.isNotEmpty()) d.govPref?.value = gov
    }

    private fun nearestIndex(d: Domain, v: Long): Int =
        d.freqs.indices.minByOrNull { Math.abs(d.freqs[it] - v) } ?: 0

    private fun mhz(d: Domain, v: Long) = "${v / d.unit} MHz"

    private fun applyFreq(d: Domain, isMin: Boolean, index: Int): Boolean {
        val v = d.freqs.getOrNull(index) ?: return false
        val curMin = readFile(d.node(d.minName)).toLongOrNull() ?: v
        val curMax = readFile(d.node(d.maxName)).toLongOrNull() ?: v

        if (isMin) {
            // min above max: raise max first so the kernel accepts it
            if (v > curMax) {
                writeFile(d.node(d.maxName), v.toString())
                d.maxPref?.apply { value = index; summary = mhz(d, v) }
            }
            writeFile(d.node(d.minName), v.toString())
            d.minPref?.summary = mhz(d, v)
        } else {
            // max below min: lower min first
            if (v < curMin) {
                writeFile(d.node(d.minName), v.toString())
                d.minPref?.apply { value = index; summary = mhz(d, v) }
            }
            writeFile(d.node(d.maxName), v.toString())
            d.maxPref?.summary = mhz(d, v)
        }
        return true
    }

    // ------------------------------------------------------------- listener

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        for (d in domains) {
            when {
                preference === d.minPref -> return applyFreq(d, true, (newValue as Number).toInt())
                preference === d.maxPref -> return applyFreq(d, false, (newValue as Number).toInt())
                preference === d.govPref -> {
                    writeFile(d.node(d.govName), newValue as String)
                    return true
                }
            }
        }

        val requestedState = newValue as Boolean
        for (i in 0 until NUM_CORES) {
            if (preference === mCorePrefs[i]) {
                if (!requestedState && !canOffline(i)) {
                    Toast.makeText(
                        context,
                        "At least 2 little cores must remain online",
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                setCoreState(i, requestedState)
                return true
            }
        }
        return false
    }

    // ----------------------------------------------------------------- core

    private fun isCoreOnline(core: Int): Boolean {
        val path = "$CPU/cpu$core/online"
        return if (File(path).exists()) readFile(path) == "1" else File("$CPU/cpu$core").exists()
    }

    private fun setCoreState(core: Int, online: Boolean) {
        writeFile("$CPU/cpu$core/online", if (online) "1" else "0")
    }

    private fun canOffline(core: Int): Boolean {
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

    // ------------------------------------------------------------------- io

    private fun readFile(path: String): String {
        return try {
            String(Files.readAllBytes(File(path).toPath())).trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $path", e)
            ""
        }
    }

    private fun writeFile(path: String, value: String) {
        try {
            Files.write(File(path).toPath(), value.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $path", e)
        }
    }
}
