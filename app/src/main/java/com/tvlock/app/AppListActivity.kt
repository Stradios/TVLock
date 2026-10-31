package com.tvlock.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

/**
 * The settings screen: pick which apps need a PIN, and check that protection is switched on.
 * It can only be reached through MainActivity (PIN first) and closes itself when you leave it,
 * so coming back always asks for the PIN again.
 */
class AppListActivity : Activity() {

    private companion object {
        /** Longest time away on a system settings screen before the PIN is asked again. */
        const val SETTINGS_TRIP_LIMIT_MS = 2 * 60 * 1000L
    }

    private lateinit var store: PinStore
    private lateinit var statusText: TextView
    private lateinit var btnService: Button
    private lateinit var btnOverlay: Button
    private lateinit var loadingText: TextView
    private lateinit var recycler: RecyclerView
    private var leftForSettingsAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Session.unlocked) {
            sendBackToPinScreen()
            return
        }
        setContentView(R.layout.activity_app_list)
        store = PinStore(this)

        statusText = findViewById(R.id.statusText)
        btnService = findViewById(R.id.btnService)
        btnOverlay = findViewById(R.id.btnOverlay)
        loadingText = findViewById(R.id.loadingText)
        recycler = findViewById(R.id.appList)
        recycler.layoutManager = LinearLayoutManager(this)

        btnService.setOnClickListener {
            openSystemScreen(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
        }
        btnOverlay.setOnClickListener {
            openSystemScreen(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                null
            )
        }

        loadApps()
    }

    override fun onStart() {
        super.onStart()
        if (leftForSettingsAt != 0L) {
            val away = SystemClock.elapsedRealtime() - leftForSettingsAt
            leftForSettingsAt = 0L
            if (away > SETTINGS_TRIP_LIMIT_MS) sendBackToPinScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::recycler.isInitialized) refreshStatus()
    }

    override fun onStop() {
        super.onStop()
        // Leaving this screen (Home, standby, another app) closes it, so the PIN is needed again.
        // The one exception is a short trip to the system settings to switch permissions on.
        if (leftForSettingsAt == 0L) finish()
    }

    override fun onDestroy() {
        if (isFinishing) Session.unlocked = false
        super.onDestroy()
    }

    private fun sendBackToPinScreen() {
        Session.unlocked = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun refreshStatus() {
        val serviceOn = AppLockService.isEnabled(this)
        val overlayOn = Settings.canDrawOverlays(this)

        btnService.visibility = if (serviceOn) View.GONE else View.VISIBLE
        btnOverlay.visibility = if (overlayOn) View.GONE else View.VISIBLE

        when {
            !serviceOn -> {
                statusText.setText(R.string.status_service_off)
                statusText.setTextColor(getColor(R.color.warn))
            }
            !overlayOn -> {
                statusText.setText(R.string.status_overlay_off)
                statusText.setTextColor(getColor(R.color.warn))
            }
            else -> {
                statusText.setText(R.string.status_on)
                statusText.setTextColor(getColor(R.color.ok))
            }
        }
    }

    private fun openSystemScreen(primary: Intent, fallback: Intent?) {
        leftForSettingsAt = SystemClock.elapsedRealtime()
        for (candidate in listOfNotNull(primary, fallback)) {
            try {
                startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // This TV does not have that screen. Try the next one.
            }
        }
        leftForSettingsAt = 0L
        Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun loadApps() {
        thread {
            val entries = queryApps()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                loadingText.visibility = View.GONE
                recycler.adapter = AppAdapter(entries) { entry ->
                    store.setLocked(entry.packageName, entry.locked)
                }
                // With nothing to fix above the list, start with the first app selected.
                if (btnService.visibility != View.VISIBLE && btnOverlay.visibility != View.VISIBLE) {
                    recycler.post { recycler.getChildAt(0)?.requestFocus() }
                }
            }
        }
    }

    /** Every app that has an icon on the TV home screen, plus the system Settings app. */
    private fun queryApps(): List<AppEntry> {
        val pm = packageManager
        val locked = store.lockedPackages()
        val found = LinkedHashMap<String, AppEntry>()

        fun add(info: ResolveInfo) {
            val pkg = info.activityInfo?.packageName ?: return
            if (pkg == packageName || found.containsKey(pkg)) return
            val label = info.loadLabel(pm)?.toString() ?: pkg
            found[pkg] = AppEntry(pkg, label, info.loadIcon(pm), pkg in locked)
        }

        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            queryActivities(Intent(Intent.ACTION_MAIN).addCategory(category)).forEach { add(it) }
        }
        queryActivities(Intent(Settings.ACTION_SETTINGS)).firstOrNull()?.let { add(it) }

        return found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    @Suppress("DEPRECATION")
    private fun queryActivities(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }
    }
}
