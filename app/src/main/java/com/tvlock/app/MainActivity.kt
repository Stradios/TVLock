package com.tvlock.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent

/**
 * The door to TV Lock itself.
 * First run: create and confirm a 4 to 6 digit PIN.
 * Every run after that: enter the PIN before the app list can be opened.
 */
class MainActivity : Activity() {

    private enum class Step { CREATE, CONFIRM, UNLOCK }

    private lateinit var store: PinStore
    private lateinit var pad: PinPad
    private var step = Step.UNLOCK
    private var firstPin = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PinStore(this)
        pad = PinPad(findViewById(R.id.pinPad)) { pin -> onPinEntered(pin) }
        showStep(if (store.hasPin()) Step.UNLOCK else Step.CREATE)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        pad.handleKey(event) || super.dispatchKeyEvent(event)

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step == Step.CONFIRM) {
            firstPin = ""
            showStep(Step.CREATE)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        pad.release()
        super.onDestroy()
    }

    private fun showStep(newStep: Step) {
        step = newStep
        pad.clear()
        when (newStep) {
            Step.CREATE -> {
                pad.slots = PinStore.MAX_PIN_LENGTH
                pad.autoSubmitLength = 0
                pad.setTitle(getString(R.string.create_pin_title))
                pad.setMessage(getString(R.string.create_pin_hint))
            }
            Step.CONFIRM -> {
                pad.slots = PinStore.MAX_PIN_LENGTH
                pad.autoSubmitLength = 0
                pad.setTitle(getString(R.string.confirm_pin_title))
                pad.setMessage(getString(R.string.confirm_pin_hint))
            }
            Step.UNLOCK -> {
                pad.slots = store.pinLength().coerceIn(PinStore.MIN_PIN_LENGTH, PinStore.MAX_PIN_LENGTH)
                pad.autoSubmitLength = pad.slots
                pad.setTitle(getString(R.string.enter_pin_title))
                pad.setMessage(getString(R.string.enter_pin_hint))
                val remaining = store.lockoutRemainingMs()
                if (remaining > 0) startLockout(remaining)
            }
        }
        pad.focusDefault()
    }

    private fun onPinEntered(pin: String) {
        when (step) {
            Step.CREATE -> {
                if (pin.length < PinStore.MIN_PIN_LENGTH) {
                    pad.clear()
                    pad.setMessage(getString(R.string.pin_too_short), true)
                    return
                }
                firstPin = pin
                showStep(Step.CONFIRM)
            }

            Step.CONFIRM -> {
                if (pin == firstPin) {
                    store.setPin(pin)
                    openAppList()
                } else {
                    firstPin = ""
                    showStep(Step.CREATE)
                    pad.setMessage(getString(R.string.pins_do_not_match), true)
                }
            }

            Step.UNLOCK -> {
                if (store.verify(pin)) {
                    openAppList()
                } else {
                    pad.clear()
                    val remaining = store.lockoutRemainingMs()
                    if (remaining > 0) {
                        startLockout(remaining)
                    } else {
                        pad.setMessage(getString(R.string.wrong_pin), true)
                    }
                }
            }
        }
    }

    private fun startLockout(remainingMs: Long) {
        pad.lockFor(remainingMs) { pad.setMessage(getString(R.string.enter_pin_hint)) }
    }

    private fun openAppList() {
        Session.unlocked = true
        startActivity(Intent(this, AppListActivity::class.java))
        finish()
    }
}
