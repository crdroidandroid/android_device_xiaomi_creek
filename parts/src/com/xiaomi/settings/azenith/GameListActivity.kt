package com.xiaomi.settings.azenith

import android.app.Activity
import android.os.Bundle
import android.os.ServiceManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.CheckBox
import android.widget.Toast
import android.view.ViewGroup
import android.view.Gravity
import android.graphics.Color
import android.graphics.Typeface
import android.content.pm.ApplicationInfo
import org.json.JSONArray
import vendor.azenith.IAZenithService

class GameListActivity : Activity() {

    private var service: IAZenithService? = null
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        service = try {
            IAZenithService.Stub.asInterface(
                ServiceManager.getService(
                    "vendor.azenith.IAZenithService/default"
                )
            )
        } catch (_: Exception) {
            null
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 24, 32, 24)
        root.setBackgroundColor(Color.BLACK)

        val title = TextView(this)
        title.text = "AZenith GameList"
        title.textSize = 24f
        title.setTextColor(Color.WHITE)
        title.setTypeface(null, Typeface.BOLD)
        root.addView(title)

        val status = TextView(this)
        status.text = if (service == null) {
            "AZenith Binder service unavailable"
        } else {
            "Select applications to enable AZenith"
        }
        status.setTextColor(Color.LTGRAY)
        status.setPadding(0, 12, 0, 24)
        root.addView(status)

        container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL

        val scroll = android.widget.ScrollView(this)
        scroll.addView(container)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
        loadApps()
    }

    private fun getGames(): Set<String> {
        val raw = try {
            service?.games ?: ""
        } catch (_: Exception) {
            ""
        }

        if (raw.isBlank()) return emptySet()

        return try {
            val json = JSONArray(raw)
            (0 until json.length())
                .map { json.optString(it) }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (_: Exception) {
            raw.split(',', '\n')
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }

    private fun loadApps() {
        container.removeAllViews()

        val currentGames = getGames()

        val apps = packageManager
            .getInstalledApplications(0)
            .filter {
                packageManager.getLaunchIntentForPackage(it.packageName) != null &&
                it.packageName != packageName
            }
            .sortedBy {
                packageManager.getApplicationLabel(it).toString()
                    .lowercase()
            }

        for (app in apps) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(8, 12, 8, 12)

            val label = TextView(this)
            label.text = packageManager.getApplicationLabel(app)
            label.textSize = 16f
            label.setTextColor(Color.WHITE)
            label.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )

            val check = CheckBox(this)
            check.isChecked = currentGames.contains(app.packageName)
            check.setTextColor(Color.WHITE)

            check.setOnCheckedChangeListener { _, checked ->
                val ok = try {
                    if (checked) {
                        service?.addGame(app.packageName) == true
                    } else {
                        service?.removeGame(app.packageName) == true
                    }
                } catch (_: Exception) {
                    false
                }

                if (!ok) {
                    check.isChecked = !checked
                    Toast.makeText(
                        this,
                        "Failed to update ${app.packageName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            row.addView(label)
            row.addView(check)
            container.addView(row)

            val divider = android.view.View(this)
            divider.setBackgroundColor(0xFF333333.toInt())
            container.addView(
                divider,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                )
            )
        }
    }
}
