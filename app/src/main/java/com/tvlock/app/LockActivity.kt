package com.tvlock.app

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * The PIN screen that AppLockService puts on top of a locked app.
 * Correct PIN: the locked app is revealed. Back or "Back to home": go to the home screen.
 */
class LockActivity : Activity() {

    companion object {
        const val EXTRA_PACKAGE = "com.tvlock.app.extra.PACKAGE"
    }

    private lateinit var store: PinStore
    private lateinit var pad: PinPad
    private var targetPackage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        store = PinStore(this)
        pad = PinPad(findViewById(R.id.pinPad)) { pin -> onPinEntered(pin) }
        pad.slots = store.pinLength().coerceIn(PinStore.MIN_PIN_LENGTH, PinStore.MAX_PIN_LENGTH)
        pad.autoSubmitLength = pad.slots
        pad.setTitle(getString(R.string.enter_pin_title))
        findViewById<View>(R.id.btnCancel).setOnClickListener { goHome() }
        bindTarget(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindTarget(intent)
    }

    override fun onStart() {
        super.onStart()
        AppLockService.lockScreenVisible = true
    }

    override fun onStop() {
        AppLockService.lockScreenVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        pad.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        pad.handleKey(event) || super.dispatchKeyEvent(event)

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun bindTarget(source: Intent?) {
        targetPackage = source?.getStringExtra(EXTRA_PACKAGE) ?: ""

        var label: CharSequence = getString(R.string.unknown_app)
        var icon: Drawable? = null
        try {
            @Suppress("DEPRECATION")
            val info = packageManager.getApplicationInfo(targetPackage, 0)
            label = packageManager.getApplicationLabel(info)
            icon = packageManager.getApplicationIcon(info)
        } catch (e: Exception) {
            // Unknown package: keep the generic wording.
        }
        findViewById<TextView>(R.id.lockedAppName).text = getString(R.string.lock_title_format, label)
        findViewById<ImageView>(R.id.lockedAppIcon).setImageDrawable(icon)

        pad.clear()
        pad.setMessage(getString(R.string.lock_subtitle))
        val remaining = store.lockoutRemainingMs()
        if (remaining > 0) {
            pad.lockFor(remaining) { pad.setMessage(getString(R.string.lock_subtitle)) }
        }
        pad.focusDefault()
    }

    private fun onPinEntered(pin: String) {
        if (store.verify(pin)) {
            // Remember the unlock so the app is not blocked again while it stays on screen.
            AppLockService.unlockedPackage = targetPackage
            finish()
        } else {
            pad.clear()
            val remaining = store.lockoutRemainingMs()
            if (remaining > 0) {
                pad.lockFor(remaining) { pad.setMessage(getString(R.string.lock_subtitle)) }
            } else {
                pad.setMessage(getString(R.string.wrong_pin), true)
            }
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }
}
