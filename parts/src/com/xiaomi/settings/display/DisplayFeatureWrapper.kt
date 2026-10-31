/*
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.os.IHwBinder
import android.util.Log
import vendor.xiaomi.hardware.displayfeature.V1_0.IDisplayFeature

object DisplayFeatureWrapper {
    private const val TAG = "DisplayFeatureWrapper"
    private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

    @Volatile private var displayFeature: IDisplayFeature? = null

    private val deathRecipient =
        IHwBinder.DeathRecipient {
            if (DEBUG) Log.d(TAG, "DisplayFeature service died")
            displayFeature = null
        }

    @Synchronized
    private fun getDisplayFeature(): IDisplayFeature? {
        displayFeature?.let { return it }
        return try {
            val service = IDisplayFeature.getService(true)
            service?.linkToDeath(deathRecipient, 0)
            displayFeature = service
            if (DEBUG) Log.d(TAG, "Connected to DisplayFeature HIDL service")
            service
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get DisplayFeature service", e)
            null
        }
    }

    fun setFeature(
        mode: Int,
        value: Int,
        cookie: Int,
    ) {
        Thread {
            val feature = getDisplayFeature()
            if (feature == null) {
                if (DEBUG) Log.d(TAG, "DisplayFeature is null, skipping setFeature")
                return@Thread
            }
            try {
                if (DEBUG) Log.d(TAG, "setFeature: mode=$mode value=$value cookie=$cookie")
                feature.setFeature(0, mode, value, cookie) // displayId 0
            } catch (e: Exception) {
                Log.e(TAG, "setFeature failed!", e)
                displayFeature = null
            }
        }.start()
    }
}
