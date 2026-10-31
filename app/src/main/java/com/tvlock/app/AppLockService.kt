package com.tvlock.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

/**
 * Watches which app comes to the front. When a locked app appears, the PIN screen
 * (LockActivity) is shown on top of it. The service only reads the name of the app
 * on screen, never its content.
 */
class AppLockService : AccessibilityService() {

    companion object {
        /** The locked app that was unlocked and is still on screen. */
        @Volatile
        var unlockedPackage: String? = null

        /** True while the PIN screen is on screen. */
        @Volatile
        var lockScreenVisible = false

        @Volatile
        private var foregroundPackage: String? = null

        private const val FAILSAFE_DELAY_MS = 1500L

        /** System surfaces that appear over apps and must not count as "leaving" the app. */
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, AppLockService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }

    private val store by lazy { PinStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var keyboardPackages: Set<String> = emptySet()

    // When the TV goes to standby, every locked app needs the PIN again.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            unlockedPackage = null
            foregroundPackage = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        keyboardPackages = try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Our own PIN screens, the on-screen keyboard and system pop-ups are not "another app".
        if (pkg == packageName || pkg in IGNORED_PACKAGES || pkg in keyboardPackages) return

        foregroundPackage = pkg
        if (store.isLocked(pkg)) {
            if (unlockedPackage != pkg) showLockScreen(pkg)
        } else {
            // The person moved on to a different app, so the next visit to a locked app asks again.
            unlockedPackage = null
        }
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            // Was never registered.
        }
        unlockedPackage = null
        foregroundPackage = null
        lockScreenVisible = false
        super.onDestroy()
    }

    private fun showLockScreen(pkg: String) {
        val intent = Intent(this, LockActivity::class.java)
            .putExtra(LockActivity.EXTRA_PACKAGE, pkg)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            goHome()
            return
        }

        // Fail-safe: if Android refused to show the PIN screen, never leave the app open.
        handler.postDelayed({
            if (!lockScreenVisible && foregroundPackage == pkg && unlockedPackage != pkg) {
                goHome()
            }
        }, FAILSAFE_DELAY_MS)
    }

    private fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
