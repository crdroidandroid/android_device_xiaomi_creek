/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.corecontrol

import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
import com.xiaomi.settings.R
import com.xiaomi.settings.corecontrol.CpuManagerUtils.Domain
import com.xiaomi.settings.corecontrol.CpuManagerUtils.Preset

/** CPU Manager: profile preset + core on/off + min/max freq + governor for little, big and GPU. */
class CoreControlFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private class Ui(val d: Domain) {
        var freqs: List<Long> = emptyList() // ascending, in node units
        var minPref: SliderPreference? = null
        var maxPref: SliderPreference? = null
        var govPref: ListPreference? = null
    }

    private val mCorePrefs = arrayOfNulls<SwitchPreferenceCompat>(CpuManagerUtils.NUM_CORES)
    private val uis = CpuManagerUtils.domains.map { Ui(it) }
    private var presetPref: ListPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.core_control_settings, rootKey)

        setupPreset()

        context?.let { CpuManagerUtils.ensureDefaults(it) }
        findPreference<Preference>("reset_perf")?.setOnPreferenceClickListener {
            doReset()
            true
        }

        for (i in 0 until CpuManagerUtils.NUM_CORES) {
            mCorePrefs[i] = findPreference("core_$i")
            mCorePrefs[i]?.apply {
                onPreferenceChangeListener = this@CoreControlFragment
                isChecked = CpuManagerUtils.isCoreOnline(i)
            }
        }

        uis.forEach { setupDomain(it) }
    }

    override fun onResume() {
        super.onResume()
        for (i in 0 until CpuManagerUtils.NUM_CORES) {
            mCorePrefs[i]?.isChecked = CpuManagerUtils.isCoreOnline(i)
        }
        uis.forEach { refreshDomain(it) }
        updatePresetSummary()
    }

    // ---------------------------------------------------------------- preset

    private fun setupPreset() {
        val presets = Preset.values()
        presetPref = findPreference<ListPreference>("cpu_preset")?.apply {
            entries = (presets.map { getString(it.labelRes) } + getString(R.string.cpu_manager_reset))
                .toTypedArray()
            entryValues = (presets.map { it.id } + "reset").toTypedArray()
            onPreferenceChangeListener = this@CoreControlFragment
        }
        updatePresetSummary()
    }

    private fun updatePresetSummary() {
        val ctx = context ?: return
        presetPref?.summary = CpuManagerUtils.presetLabel(ctx)
    }

    // ---------------------------------------------------------------- domain

    private fun setupDomain(ui: Ui) {
        val d = ui.d
        ui.minPref = findPreference("${d.key}_min_freq")
        ui.maxPref = findPreference("${d.key}_max_freq")
        ui.govPref = findPreference("${d.key}_governor")

        ui.freqs = CpuManagerUtils.readFreqs(d)

        if (ui.freqs.isEmpty()) {
            ui.minPref?.apply { isEnabled = false; summary = getString(R.string.cpu_manager_unsupported) }
            ui.maxPref?.apply { isEnabled = false; summary = getString(R.string.cpu_manager_unsupported) }
        } else {
            listOf(ui.minPref, ui.maxPref).forEach {
                it?.apply {
                    min = 0
                    max = ui.freqs.size - 1
                    onPreferenceChangeListener = this@CoreControlFragment
                }
            }
        }

        val govs = CpuManagerUtils.readFile(d.node(d.govsName))
            .split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (govs.isEmpty()) {
            ui.govPref?.apply { isEnabled = false; summary = getString(R.string.cpu_manager_unsupported) }
        } else {
            ui.govPref?.apply {
                entries = govs.toTypedArray()
                entryValues = govs.toTypedArray()
                onPreferenceChangeListener = this@CoreControlFragment
            }
        }
        refreshDomain(ui)
    }

    private fun refreshDomain(ui: Ui) {
        val d = ui.d
        if (ui.freqs.isNotEmpty()) {
            val curMin = CpuManagerUtils.readFile(d.node(d.minName)).toLongOrNull()
            val curMax = CpuManagerUtils.readFile(d.node(d.maxName)).toLongOrNull()
            if (curMin != null) ui.minPref?.apply { value = nearestIndex(ui, curMin); summary = mhz(d, curMin) }
            if (curMax != null) ui.maxPref?.apply { value = nearestIndex(ui, curMax); summary = mhz(d, curMax) }
        }
        val gov = CpuManagerUtils.readFile(d.node(d.govName))
        if (gov.isNotEmpty()) ui.govPref?.value = gov
    }

    private fun nearestIndex(ui: Ui, v: Long): Int =
        ui.freqs.indices.minByOrNull { Math.abs(ui.freqs[it] - v) } ?: 0

    private fun mhz(d: Domain, v: Long) = "${v / d.unit} MHz"

    private fun applyFreq(ui: Ui, isMin: Boolean, index: Int): Boolean {
        val ctx = context ?: return false
        val d = ui.d
        val v = ui.freqs.getOrNull(index) ?: return false
        val curMin = CpuManagerUtils.readFile(d.node(d.minName)).toLongOrNull() ?: v
        val curMax = CpuManagerUtils.readFile(d.node(d.maxName)).toLongOrNull() ?: v

        if (isMin) {
            // min above max: raise max first so the kernel accepts it
            if (v > curMax) {
                CpuManagerUtils.writeLocked(d.node(d.maxName), v.toString())
                ui.maxPref?.apply { value = index; summary = mhz(d, v) }
                CpuManagerUtils.saveFreq(ctx, d, false, v)
            }
            CpuManagerUtils.writeLocked(d.node(d.minName), v.toString())
            ui.minPref?.summary = mhz(d, v)
            CpuManagerUtils.saveFreq(ctx, d, true, v)
        } else {
            // max below min: lower min first
            if (v < curMin) {
                CpuManagerUtils.writeLocked(d.node(d.minName), v.toString())
                ui.minPref?.apply { value = index; summary = mhz(d, v) }
                CpuManagerUtils.saveFreq(ctx, d, true, v)
            }
            CpuManagerUtils.writeLocked(d.node(d.maxName), v.toString())
            ui.maxPref?.summary = mhz(d, v)
            CpuManagerUtils.saveFreq(ctx, d, false, v)
        }
        updatePresetSummary()
        return true
    }

    private fun doReset() {
        val ctx = context ?: return
        Thread {
            CpuManagerUtils.resetToDefault(ctx)
            activity?.runOnUiThread {
                uis.forEach { refreshDomain(it) }
                updatePresetSummary()
                Toast.makeText(ctx, R.string.cpu_manager_reset_done, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // ------------------------------------------------------------- listener

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val ctx = context ?: return false

        if (preference === presetPref) {
            if (newValue == "reset") {
                doReset()
                return false
            }
            val p = Preset.fromId(newValue as? String) ?: return false
            CpuManagerUtils.applyPreset(ctx, p)
            uis.forEach { refreshDomain(it) }
            updatePresetSummary()
            return true
        }

        for (ui in uis) {
            when {
                preference === ui.minPref -> return applyFreq(ui, true, (newValue as Number).toInt())
                preference === ui.maxPref -> return applyFreq(ui, false, (newValue as Number).toInt())
                preference === ui.govPref -> {
                    CpuManagerUtils.writeLocked(ui.d.node(ui.d.govName), newValue as String)
                    CpuManagerUtils.saveGovernor(ctx, ui.d, newValue)
                    updatePresetSummary()
                    return true
                }
            }
        }

        val requestedState = newValue as Boolean
        for (i in 0 until CpuManagerUtils.NUM_CORES) {
            if (preference === mCorePrefs[i]) {
                if (!requestedState && !CpuManagerUtils.canOffline(i)) {
                    Toast.makeText(
                        ctx,
                        "At least 2 little cores must remain online",
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                CpuManagerUtils.setCoreState(i, requestedState)
                CpuManagerUtils.saveCore(ctx, i, requestedState)
                return true
            }
        }
        return false
    }
}
