/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.corecontrol

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.ContextThemeWrapper
import com.xiaomi.settings.R
import com.xiaomi.settings.corecontrol.CpuManagerUtils.Preset

/** Quick Settings tile: tap to pick a CPU profile. Default = back to system control. */
class CpuManagerTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        val presets = Preset.values()
        val labels = presets.map { getString(it.labelRes) }.toTypedArray()
        val id = CpuManagerUtils.currentPresetId(this)
        val checked = if (id == null) Preset.DEFAULT.ordinal else Preset.fromId(id)?.ordinal ?: -1
        val themed = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val dialog = AlertDialog.Builder(themed)
            .setTitle(R.string.cpu_manager_tile_label)
            .setSingleChoiceItems(labels, checked) { d, which ->
                d.dismiss()
                Thread {
                    val p = presets[which]
                    if (p == Preset.DEFAULT) CpuManagerUtils.resetToDefault(ctx)
                    else CpuManagerUtils.applyPreset(ctx, p)
                    Handler(Looper.getMainLooper()).post { updateTileState() }
                }.start()
            }
            .create()
        showDialog(dialog)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val id = CpuManagerUtils.currentPresetId(this)
        tile.label = getString(R.string.cpu_manager_tile_label)
        tile.subtitle = CpuManagerUtils.presetLabel(this)
        tile.state = if (id == null || id == "balanced" || id == "default") Tile.STATE_INACTIVE
                     else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
