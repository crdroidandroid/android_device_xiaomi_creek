/*
 * Copyright (C) 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.utils;

import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.util.Log;
import android.widget.Toast;

/**
 * Helper a QS-csempe hozzáadás kéréséhez (StatusBarManager.requestAddTileService).
 * MEGJEGYZÉS: ha a klee fában már létezik TileUtils, ezt a fájlt hagyd ki,
 * vagy fésüld össze a meglévővel.
 */
public final class TileUtils {

    private static final String TAG = "TileUtils";

    private TileUtils() {}

    public static void requestAddTileService(Context context, Class<?> tileServiceClass,
            int labelRes, int iconRes) {
        try {
            StatusBarManager sbm = context.getSystemService(StatusBarManager.class);
            if (sbm == null) {
                Log.w(TAG, "StatusBarManager unavailable");
                return;
            }
            sbm.requestAddTileService(
                    new ComponentName(context, tileServiceClass),
                    context.getString(labelRes),
                    Icon.createWithResource(context, iconRes),
                    context.getMainExecutor(),
                    result -> Log.d(TAG, "requestAddTileService result: " + result));
        } catch (Exception e) {
            Log.e(TAG, "Failed to request tile add", e);
            Toast.makeText(context, "Unable to request tile", Toast.LENGTH_SHORT).show();
        }
    }
}
