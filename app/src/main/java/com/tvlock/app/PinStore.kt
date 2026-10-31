package com.tvlock.app

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the PIN (as a salted hash, never as plain text), the list of locked apps,
 * and the wrong-attempt lockout state.
 */
class PinStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- PIN

    fun hasPin(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun pinLength(): Int = prefs.getInt(KEY_LENGTH, MIN_PIN_LENGTH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt(KEY_LENGTH, pin.length)
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .apply()
    }

    /** Returns true if [pin] is correct. Counts wrong attempts and may start a lockout. */
    fun verify(pin: String): Boolean {
        if (lockoutRemainingMs() > 0) return false
        val saltText = prefs.getString(KEY_SALT, null) ?: return false
        val hashText = prefs.getString(KEY_HASH, null) ?: return false

        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        val actual = hash(pin, Base64.decode(saltText, Base64.NO_WRAP))
        val ok = MessageDigest.isEqual(expected, actual)

        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
        } else {
            registerFailure()
        }
        return ok
    }

    /** Milliseconds left of the "too many wrong attempts" pause, or 0 if there is none. */
    fun lockoutRemainingMs(): Long {
        val left = prefs.getLong(KEY_LOCKED_UNTIL, 0L) - System.currentTimeMillis()
        return left.coerceIn(0L, MAX_LOCKOUT_SECONDS * 1000L)
    }

    private fun registerFailure() {
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILS, fails)
        if (fails % ATTEMPTS_BEFORE_LOCKOUT == 0) {
            // 30 s, 60 s, 120 s, 240 s, then 5 minutes for every further round.
            val round = fails / ATTEMPTS_BEFORE_LOCKOUT
            val seconds = minOf(30L shl (round - 1).coerceAtMost(4), MAX_LOCKOUT_SECONDS)
            editor.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + seconds * 1000L)
        }
        editor.apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, HASH_ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    // -------------------------------------------------------- Locked apps

    fun lockedPackages(): Set<String> =
        HashSet(prefs.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet())

    fun isLocked(packageName: String): Boolean =
        prefs.getStringSet(KEY_LOCKED, null)?.contains(packageName) == true

    fun setLocked(packageName: String, locked: Boolean) {
        // SharedPreferences must be given a fresh copy of the set, never the one it returned.
        val updated = lockedPackages().toMutableSet()
        if (locked) updated.add(packageName) else updated.remove(packageName)
        prefs.edit().putStringSet(KEY_LOCKED, updated).apply()
    }

    companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 6

        private const val PREFS_NAME = "tvlock"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_LENGTH = "pin_length"
        private const val KEY_FAILS = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"
        private const val KEY_LOCKED = "locked_packages"

        private const val HASH_ITERATIONS = 20_000
        private const val ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val MAX_LOCKOUT_SECONDS = 300L
    }
}

/** In-memory flag: true only while the PIN has been entered and the app list is open. */
object Session {
    @Volatile
    var unlocked = false
}
