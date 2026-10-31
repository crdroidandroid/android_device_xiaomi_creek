/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Közös konstansok a PocoParts-hoz (klee / MT6899).
 *
 * A backlight node futásidőben oldódik fel: klee-n a MediaTek-útvonal
 * (/sys/class/leds/lcd-backlight) él — az init.mt6899.rc jogosítja
 * (system:system 0664) —, a Qualcomm-útvonal csak tartalék.
 *
 * A max fényerő nem fix érték: az MTK leds driver logic_max_brightness
 * node-ja adja (ezt jogosítja a stock init), fallbackként a szabványos
 * max_brightness. Ez a HBM-tartománnyal együtt értendő logikai skála.
 */
public final class Constants {

    private Constants() {
        // no instances
    }

    // =========================================================
    // AutoHbm — SharedPreferences kulcsok
    // =========================================================
    public static final String KEY_AUTO_HBM = "auto_hbm";
    public static final String KEY_AUTO_HBM_THRESHOLD = "auto_hbm_threshold";
    public static final String KEY_AUTO_HBM_ENABLE_TIME = "auto_hbm_enable_time";
    public static final String KEY_AUTO_HBM_DISABLE_TIME = "auto_hbm_disable_time";
    public static final String KEY_CURRENT_LUX_LEVEL = "current_lux_level";

    // =========================================================
    // Backlight node (klee, futásidőben feloldva)
    // =========================================================
    public static final String NODE_BRIGHTNESS = resolveBrightnessNode();

    private static String resolveBrightnessNode() {
        final String[] candidates = {
                "/sys/class/leds/lcd-backlight/brightness",         // MediaTek (klee)
                "/sys/class/backlight/panel0-backlight/brightness"  // Qualcomm fallback
        };
        for (String c : candidates) {
            if (new File(c).exists()) return c;
        }
        return candidates[0];
    }

    /**
     * A panel maximális (logikai) fényereje a kernelből, lustán cache-elve.
     * Sorrend: logic_max_brightness (MTK) -> max_brightness (szabványos).
     * Végső tartalék: 2047.
     */
    private static int sMaxBrightness = -1;

    public static synchronized int getMaxBrightness() {
        if (sMaxBrightness > 0) return sMaxBrightness;

        final String base = NODE_BRIGHTNESS.substring(0, NODE_BRIGHTNESS.lastIndexOf('/'));
        final String[] maxNodes = {
                base + "/logic_max_brightness",  // MTK (klee) — a stock init jogosítja
                base + "/max_brightness"         // szabványos leds/backlight
        };
        for (String node : maxNodes) {
            final String v = readFirstLine(node);
            if (v != null) {
                try {
                    final int max = Integer.parseInt(v.trim());
                    if (max > 0) {
                        sMaxBrightness = max;
                        return max;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        sMaxBrightness = 2047;
        return sMaxBrightness;
    }

    /** Belső, függőségmentes egysoros fájlolvasó. */
    private static String readFirstLine(String fileName) {
        final File f = new File(fileName);
        if (!f.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(f), 512)) {
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }
}
