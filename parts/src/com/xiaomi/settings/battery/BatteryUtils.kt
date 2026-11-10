/*
 * Copyright (C) 2023-2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.battery

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

object BatteryUtils {
    private const val TAG = "XiaomiBatteryUtils"
    const val PREF_CHARGING_CTRL = "charging_control"
    const val PREF_CHARGING_LIMIT = "charging_limit"
    const val PREF_CHARGE_BYPASS = "charge_bypass"
    private const val NODE_SUSPEND = "/sys/class/qcom-battery/input_suspend"

    const val PREF_CHARGING_POWER = "charging_power_profile"
    const val NODE_FCC = "/sys/class/power_supply/battery/constant_charge_current"
    const val NODE_SIC_MODE = "/sys/class/power_supply/usb/sic_mode"
    private const val FCC_MAX_UA = 22_000_000L

    fun isBypassSupported() = File(NODE_SUSPEND).exists()

    // preset wattage -> current cap (uA); 0 = system default
    private fun fccForProfile(watts: Int): Long? = when (watts) {
        33 -> 8_067_000L
        65 -> 15_889_000L
        85 -> 20_778_000L
        90 -> FCC_MAX_UA
        else -> null
    }

    fun isPowerProfileSupported() = File(NODE_FCC).exists() && File(NODE_SIC_MODE).exists()

    private fun writeNode(path: String, value: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return try {
            FileOutputStream(file).use { it.write("$value\n".toByteArray(StandardCharsets.UTF_8)) }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write $value to $path", e)
            false
        }
    }

    private fun readNode(path: String): Long? = try {
        File(path).readText().trim().toLong()
    } catch (e: Exception) {
        null
    }

    /** 0 hands control back to the system default. */
    fun applyChargingPowerProfile(watts: Int) {
        if (!isPowerProfileSupported()) return
        if (watts == 0) {
            writeNode(NODE_SIC_MODE, "0")
            writeNode(NODE_FCC, FCC_MAX_UA.toString())
            return
        }
        val target = fccForProfile(watts) ?: return
        if (!writeNode(NODE_FCC, target.toString())) return
        val live = readNode(NODE_FCC)
        if (live != null && live > target) {
            Log.e(TAG, "FCC verify failed: requested <= $target, live $live")
            return
        }
        // switch charger to fast-charge mode
        writeNode(NODE_SIC_MODE, "8")
    }

    fun applyChargingPowerProfileAsync(watts: Int) {
        ioScope.launch { applyChargingPowerProfile(watts) }
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setChargingSuspend(suspend: Boolean) {
        val file = File(NODE_SUSPEND)
        if (!file.exists()) return
        val value = if (suspend) "1" else "0"
        try {
            FileOutputStream(file).use { fos ->
                fos.write(value.toByteArray(StandardCharsets.UTF_8))
                fos.flush()
                try {
                    fos.fd.sync()
                } catch (ignored: IOException) {}
            }
            Log.d(TAG, "Successfully wrote $value to $NODE_SUSPEND")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to $NODE_SUSPEND", e)
        }
    }

    fun setChargingSuspendAsync(suspend: Boolean) {
        ioScope.launch {
            setChargingSuspend(suspend)
        }
    }
}
