/*
 * Copyright (C) 2024 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.xiaomi.settings.touchsampling

import android.content.Context
import android.util.Log
import com.xiaomi.settings.utils.writeLine
import java.io.File

object TouchSamplingUtils {

    private const val TAG = "TouchSamplingUtils"

    // Urutan: node goodix (rodin & sepolicy creek sudah kenal goodix_ts.0), lalu touch_dev
    private val CANDIDATES = arrayOf(
        "/sys/devices/platform/goodix_ts.0/switch_report_rate",
        "/sys/class/touch/touch_dev/switch_report_rate"
    )

    @JvmStatic
    val HTSR_FILE: String
        get() = CANDIDATES.firstOrNull { File(it).exists() } ?: CANDIDATES[0]

    @JvmStatic
    fun isSupported(): Boolean = CANDIDATES.any { File(it).exists() }

    @JvmStatic
    fun restoreSamplingValue(context: Context) {

        val sharedPref = context.getSharedPreferences(
            TouchSamplingSettingsFragment.SHAREDHTSR,
            Context.MODE_PRIVATE
        )

        val htsrState = sharedPref.getInt(
            TouchSamplingSettingsFragment.SHAREDHTSR,
            0
        )

        if (!writeLine(HTSR_FILE, htsrState.toString())) {
            Log.e(TAG, "Failed to restore sampling value")
        }
    }
}
