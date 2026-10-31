package com.tvlock.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

/**
 * Drives the on-screen number pad (view_pin_pad.xml): digit keys, Delete, OK,
 * number keys on the remote, and a countdown while PIN entry is paused.
 */
class PinPad(private val root: View, private val onSubmit: (String) -> Unit) {

    private val titleView: TextView = root.findViewById(R.id.pinTitle)
    private val messageView: TextView = root.findViewById(R.id.pinMessage)
    private val dotsView: TextView = root.findViewById(R.id.pinDots)
    private val handler = Handler(Looper.getMainLooper())
    private val digits = StringBuilder()
    private var countdown: Runnable? = null
    private var lockedOut = false

    /** Number of dots shown, which is also the most digits that can be typed. */
    var slots: Int = PinStore.MAX_PIN_LENGTH
        set(value) {
            field = value
            render()
        }

    /** When above 0, the PIN is submitted automatically after this many digits. */
    var autoSubmitLength: Int = 0

    init {
        val digitKeys = intArrayOf(
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9
        )
        for (digit in digitKeys.indices) {
            root.findViewById<View>(digitKeys[digit]).setOnClickListener { addDigit(digit) }
        }
        root.findViewById<View>(R.id.keyDelete).setOnClickListener { deleteDigit() }
        root.findViewById<View>(R.id.keyOk).setOnClickListener { submit() }
        render()
    }

    fun setTitle(text: CharSequence) {
        titleView.text = text
    }

    fun setMessage(text: CharSequence, isError: Boolean = false) {
        messageView.text = text
        messageView.setTextColor(
            root.context.getColor(if (isError) R.color.error else R.color.text_secondary)
        )
    }

    fun clear() {
        digits.setLength(0)
        render()
    }

    fun focusDefault() {
        root.findViewById<View>(R.id.key5).requestFocus()
    }

    /** Lets the number keys and Delete on a physical remote work. Returns true if handled. */
    fun handleKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        val digit = when (code) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
            else -> -1
        }
        if (digit >= 0) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) addDigit(digit)
            return true
        }
        if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) deleteDigit()
            return true
        }
        return false
    }

    /** Blocks input for [durationMs], showing a countdown, then calls [onDone]. */
    fun lockFor(durationMs: Long, onDone: () -> Unit) {
        stopCountdown()
        lockedOut = true
        clear()
        val endsAt = SystemClock.elapsedRealtime() + durationMs
        val tick = object : Runnable {
            override fun run() {
                val left = endsAt - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    lockedOut = false
                    countdown = null
                    onDone()
                } else {
                    setMessage(
                        root.context.getString(R.string.too_many_attempts, (left + 999) / 1000),
                        true
                    )
                    handler.postDelayed(this, 250)
                }
            }
        }
        countdown = tick
        handler.post(tick)
    }

    /** Call from onDestroy so no timer keeps running. */
    fun release() {
        stopCountdown()
    }

    private fun stopCountdown() {
        countdown?.let { handler.removeCallbacks(it) }
        countdown = null
    }

    private fun addDigit(digit: Int) {
        if (lockedOut || digits.length >= slots) return
        digits.append(digit)
        render()
        if (autoSubmitLength > 0 && digits.length == autoSubmitLength) submit()
    }

    private fun deleteDigit() {
        if (lockedOut || digits.isEmpty()) return
        digits.setLength(digits.length - 1)
        render()
    }

    private fun submit() {
        if (lockedOut || digits.isEmpty()) return
        onSubmit(digits.toString())
    }

    private fun render() {
        dotsView.text = (0 until slots).joinToString(" ") { if (it < digits.length) "●" else "○" }
    }
}
