package com.xiaomi.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import com.xiaomi.settings.display.ColorService
import com.xiaomi.settings.battery.ChargingLimitService
import com.xiaomi.settings.refreshrate.RefreshService
import com.xiaomi.settings.thermal.ThermalUtils
import com.xiaomi.settings.touchsampling.TouchSamplingService
import com.xiaomi.settings.touchsampling.TouchSamplingTileService
import com.xiaomi.settings.touchsampling.TouchSamplingUtils

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (DEBUG) Log.d(TAG, "Received boot completed intent: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> onLockedBootCompleted(context)
        }
    }

    private fun onLockedBootCompleted(context: Context) {
        // Display
        context.startServiceAsUser(Intent(context, ColorService::class.java), UserHandle.CURRENT)

        // Touch Sampling (cuma kalau kernel punya node report rate)
        if (TouchSamplingUtils.isSupported()) {
            context.startServiceAsUser(Intent(context, TouchSamplingService::class.java), UserHandle.CURRENT)
            context.startServiceAsUser(Intent(context, TouchSamplingTileService::class.java), UserHandle.CURRENT)
        }

        // Thermal
        ThermalUtils.getInstance(context).startService()

        // Battery
        context.startServiceAsUser(Intent(context, ChargingLimitService::class.java), UserHandle.CURRENT)

        // Per App Refresh Rate
        context.startServiceAsUser(Intent(context, RefreshService::class.java), UserHandle.CURRENT)
    }
}
