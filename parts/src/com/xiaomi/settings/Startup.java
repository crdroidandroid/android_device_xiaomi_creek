/*
 * Copyright (C) 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xiaomi.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.xiaomi.settings.autohbm.AutoHbmFragment;

/**
 * Boot receiver — visszaállítja az Auto HBM állapotát bootkor.
 *
 * klee megjegyzés: a garnet Startup saturation-visszaállítása és a
 * ComponentUtils-hívások szándékosan kimaradtak — azok a garnet
 * Saturation moduljához tartoznak, ami klee-n nincs portolva.
 */
public class Startup extends BroadcastReceiver {

    private static final String TAG = "Startup";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "onReceive: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_REBOOT.equals(action)) {
            // Késleltetés, hogy a rendszer (sensor service, sysfs jogok) beálljon
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "Applying Auto HBM settings...");
                AutoHbmFragment.toggleAutoHbmService(context);
            }, 5000);
        }
    }
}
