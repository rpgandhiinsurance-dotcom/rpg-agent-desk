package com.rpginsurance.brief

/** Turns the free-form Mobile field into numbers usable in tel: and wa.me links. */
object Phones {
    /** Digits plus a leading +, for the dialer. Null when nothing usable is left. */
    fun dialable(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val plus = raw.trim().startsWith("+")
        val digits = raw.filter(Char::isDigit)
        if (digits.length < 6) return null
        return if (plus) "+$digits" else digits
    }

    /**
     * International digits for wa.me, assuming India (+91) when no country code is given:
     * "98765 43210" → "919876543210", "098765 43210" → "919876543210", "+44 7700 900123" → "447700900123".
     */
    fun whatsapp(raw: String?): String? {
        val d = dialable(raw) ?: return null
        val digits = d.removePrefix("+")
        return when {
            d.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
            digits.length == 10 -> "91$digits"
            else -> digits
        }
    }
}
