/*
 * Copyright (C) 2025-2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xiaomi.settings.hub

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xiaomi.settings.R
import com.xiaomi.settings.battery.ChargingControlActivity
import com.xiaomi.settings.aboutme.AboutMeActivity
import com.xiaomi.settings.autohbm.AutoHbmActivity
import com.xiaomi.settings.corecontrol.CoreControlActivity
import com.xiaomi.settings.gamebar.GameBarSettingsActivity
import com.xiaomi.settings.resolution.ResolutionActivity
import com.xiaomi.settings.resolution.SystemResolutionActivity
import com.xiaomi.settings.speaker.ClearSpeakerActivity
import com.xiaomi.settings.thermal.ThermalSettingsActivity
import com.xiaomi.settings.refreshrate.RefreshActivity
import com.xiaomi.settings.touchsampling.TouchSamplingSettingsActivity
import com.xiaomi.settings.touchsampling.TouchSamplingUtils

class XiaomiPartsFragment : SettingsBasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.xiaomi_parts, rootKey)

        findPreference<Preference>(KEY_TOUCH_SAMPLING)?.isVisible = TouchSamplingUtils.isSupported()

        findPreference<Preference>(KEY_GAME_BAR)?.setOnPreferenceClickListener {
            startActivity(Intent(context, GameBarSettingsActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_TOUCH_SAMPLING)?.setOnPreferenceClickListener {
            startActivity(Intent(context, TouchSamplingSettingsActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_CORE_CONTROL)?.setOnPreferenceClickListener {
            startActivity(Intent(context, CoreControlActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_CHARGING_CONTROL)?.setOnPreferenceClickListener {
            startActivity(Intent(context, ChargingControlActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_SYSTEM_RESOLUTION)?.setOnPreferenceClickListener {
            startActivity(Intent(context, SystemResolutionActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_PER_APP_RESOLUTION)?.setOnPreferenceClickListener {
            startActivity(Intent(context, ResolutionActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_AUTO_HBM)?.setOnPreferenceClickListener {
            startActivity(Intent(context, AutoHbmActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_THERMAL_PROFILES)?.setOnPreferenceClickListener {
            startActivity(Intent(context, ThermalSettingsActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_DOLBY)?.setOnPreferenceClickListener {
            launchDolby()
            true
        }

        findPreference<Preference>(KEY_CLEAR_SPEAKER)?.setOnPreferenceClickListener {
            startActivity(Intent(context, ClearSpeakerActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_ABOUT_ME)?.setOnPreferenceClickListener {
            startActivity(Intent(context, AboutMeActivity::class.java))
            true
        }

        findPreference<Preference>(KEY_PER_APP_REFRESH_RATE)?.setOnPreferenceClickListener {
            startActivity(Intent(context, RefreshActivity::class.java))
            true
        }
    }

    private fun launchDolby() {
        val pm = activity?.packageManager ?: return
        val packages = listOf(
            "com.dolby.daxappui",
            "com.motorola.dolby.dolbyui",
            "com.dolby.atmos"
        )
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }
        val directIntent = Intent("android.intent.action.MAIN").apply {
            setClassName("com.dolby.daxappui", "com.dolby.daxappui.MainActivity")
        }
        if (directIntent.resolveActivity(pm) != null) {
            startActivity(directIntent)
            return
        }
        Toast.makeText(context, R.string.dolby_not_installed, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val KEY_GAME_BAR = "parts_game_bar"
        private const val KEY_TOUCH_SAMPLING = "parts_touch_sampling"
        private const val KEY_CORE_CONTROL = "parts_core_control"
        private const val KEY_CHARGING_CONTROL = "parts_charging_control"
        private const val KEY_SYSTEM_RESOLUTION = "parts_system_resolution"
        private const val KEY_PER_APP_RESOLUTION = "parts_per_app_resolution"
        private const val KEY_DOLBY = "parts_dolby"
        private const val KEY_CLEAR_SPEAKER = "parts_clear_speaker"
        private const val KEY_THERMAL_PROFILES = "parts_thermal_profiles"
        private const val KEY_AUTO_HBM = "parts_auto_hbm"
        private const val KEY_ABOUT_ME = "parts_about_me"
        private const val KEY_PER_APP_REFRESH_RATE = "parts_per_app_refresh_rate"
    }
}
