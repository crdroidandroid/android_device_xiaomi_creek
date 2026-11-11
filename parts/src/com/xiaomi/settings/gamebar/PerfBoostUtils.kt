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

package com.xiaomi.settings.gamebar

import android.content.Context
import androidx.preference.PreferenceManager
import com.xiaomi.settings.utils.writeLine
import java.io.File

object PerfBoostUtils {

    const val PREF_PERF_BOOST = "game_bar_perf_boost"
    private const val NODE_THERMAL_BOOST = "/sys/class/thermal/thermal_message/boost"
    private const val NODE_THERMAL_BALANCE = "/sys/class/thermal/thermal_message/balance_mode"
    private const val NODE_THERMAL_SCONFIG = "/sys/class/thermal/thermal_message/sconfig"

    // Real gaming profile on Xiaomi creek is 18 (thermal-tgame.conf)
    private const val SCONFIG_GAMING = "18"
    private const val SCONFIG_NORMAL = "0"

    fun isSupported(): Boolean =
        File(NODE_THERMAL_BOOST).exists() || File(NODE_THERMAL_SCONFIG).exists()

    fun setPerformanceBoost(enable: Boolean): Boolean {
        val v = if (enable) "1" else "0"
        val sconfig = if (enable) SCONFIG_GAMING else SCONFIG_NORMAL
        val r1 = writeLine(NODE_THERMAL_BOOST, v)
        val r2 = writeLine(NODE_THERMAL_BALANCE, v)
        val r3 = writeLine(NODE_THERMAL_SCONFIG, sconfig)
        return r1 || r2 || r3
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(PREF_PERF_BOOST, false)
    }

    fun restore(context: Context) {
        if (isEnabled(context)) {
            setPerformanceBoost(true)
        }
    }
}
