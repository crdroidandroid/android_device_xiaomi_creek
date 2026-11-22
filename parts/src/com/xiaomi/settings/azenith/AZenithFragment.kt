/*
 * SPDX-FileCopyrightText: 2026 koneko_dev
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.azenith

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xiaomi.settings.R
import java.io.File

class AZenithFragment : SettingsBasePreferenceFragment() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        setPreferencesFromResource(R.xml.azenith_settings, rootKey)

        bindSwitch(
            KEY_ENABLE,
            PROP_STATE,
            "1",
            "0"
        )

        bindSwitch(
            "debugmode",
            PROP_DEBUG,
            "true",
            "false"
        )

        for (key in TOGGLES) {
            bindSwitch(
                key,
                CONF + key,
                "1",
                "0"
            )
        }

        findPreference<Preference>(KEY_RESTART)
            ?.setOnPreferenceClickListener {
                restartService()
                true
            }

        for ((key, prop) in GOVS) {
            bindGovernor(key, prop)
        }

        bindList(
            "freqoffset",
            CONF + "freqoffset",
            "100",
            null
        )

        bindList(
            "renderer",
            CONF + "renderer",
            "default",
            null
        )

        bindList(
            "schemeconfig",
            CONF + "schemeconfig",
            "none",
            "none"
        )
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val svcState = SystemProperties.get("init.svc.vendor.azenith-service", "stopped")
        val enabled = SystemProperties.get(PROP_STATE, "0") == "1"

        val summary = when {
            svcState == "running" -> R.string.azenith_status_on
            svcState == "restarting" || (enabled && svcState == "stopped") -> R.string.azenith_status_restarting
            else -> R.string.azenith_status_off
        }

        findPreference<Preference>(KEY_STATUS)
            ?.setSummary(summary)
    }

    private fun restartService() {
        Toast.makeText(
            requireContext(),
            R.string.azenith_restarting,
            Toast.LENGTH_SHORT
        ).show()

        SystemProperties.set(PROP_STATE, "0")
        refreshStatus()

        handler.postDelayed({
            SystemProperties.set(PROP_STATE, "1")
            refreshStatus()
        }, 1500)
    }

    private fun bindSwitch(
        key: String,
        prop: String,
        on: String,
        off: String
    ) {
        findPreference<SwitchPreferenceCompat>(key)?.apply {
            isChecked = SystemProperties.get(prop, off) == on

            setOnPreferenceChangeListener { _, value ->
                val enabled = value as Boolean

                SystemProperties.set(
                    prop,
                    if (enabled) on else off
                )

                if (key == KEY_ENABLE) {
                    handler.postDelayed({
                        refreshStatus()
                    }, 500)
                }

                true
            }
        }
    }

    private fun bindList(
        key: String,
        prop: String,
        default: String,
        neutral: String?
    ) {
        val pref = findPreference<ListPreference>(key)
            ?: return

        var current = SystemProperties.get(prop, "")

        if (current.isEmpty() ||
            pref.findIndexOfValue(current) < 0
        ) {
            current = default
        }

        pref.value = current

        pref.setOnPreferenceChangeListener { _, value ->
            val selected = value as String

            SystemProperties.set(
                prop,
                if (selected == neutral) "" else selected
            )

            true
        }
    }

    private fun bindGovernor(
        key: String,
        prop: String
    ) {
        val pref = findPreference<ListPreference>(key)
            ?: return

        val governors = runCatching {
            File(GOV_AVAIL)
                .readText()
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

        val entries: List<CharSequence> =
            listOf<CharSequence>(
                getString(R.string.azenith_gov_auto)
            ) + governors

        val values: List<CharSequence> =
            listOf<CharSequence>("auto") + governors

        pref.entries = entries.toTypedArray()
        pref.entryValues = values.toTypedArray()

        val current = SystemProperties.get(prop, "")

        pref.value = if (
            current.isNotEmpty() &&
            current in governors
        ) {
            current
        } else {
            "auto"
        }

        pref.setOnPreferenceChangeListener { _, value ->
            val selected = value as String

            SystemProperties.set(
                prop,
                if (selected == "auto") "" else selected
            )

            true
        }
    }

    private companion object {

        const val KEY_ENABLE = "azenith_enable"
        const val KEY_RESTART = "azenith_restart"
        const val KEY_STATUS = "azenith_status"

        const val PROP_STATE =
            "persist.sys.azenithIntg.state"

        const val PROP_DEBUG =
            "persist.sys.azenithIntg.debugmode"

        const val CONF =
            "persist.sys.azenithConfIntg."

        const val GOV_AVAIL =
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors"

        val TOGGLES = listOf(
            "litemode",
            "clearbg",
            "fstrim",
            "logd",
            "walttunes",
            "DThermal",
            "SFL",
            "malisched",
            "fpsged",
            "usefpsgo",
            "schedtunes",
            "justintime",
            "disabletrace"
        )

        val GOVS = listOf(
            "custom_default_cpu_gov" to
                "persist.sys.azenithIntg.custom_default_cpu_gov",

            "custom_powersave_cpu_gov" to
                "persist.sys.azenithIntg.custom_powersave_cpu_gov",

            "custom_performance_cpu_gov" to
                "persist.sys.azenithIntg.custom_performance_cpu_gov"
        )
    }
}
