package com.rpginsurance.brief

/**
 * Airtable IDs for the RPG Insurance Advisory base.
 *
 * Checked against the Meta API (GET /v0/meta/bases/{baseId}/tables) on 7 Nov 2026.
 * Field IDs survive renames, so the app keeps working if a column is renamed in Airtable.
 */
object Schema {
    const val BASE_ID = "appjzr6iFMV1cZPh9"

    object Policies {
        const val TABLE = "tblJWk87TryJFemwG"
        const val NAME = "Policies (Combined)"
        const val CLIENT_NAME = "flda7AAz0eFCgXjty"
        const val MOBILE = "fldXfTS3sUX4n5mIs"
        const val POLICY_NUMBER = "fldnoeN91wUUt2JF5"
        const val INSURANCE_COMPANY = "fldbtPDhl4qfjiBrp"
        const val POLICY_TYPE = "fld1qguDGNOJRtyhC"
        const val END_DATE = "fldMcsT0UNIE3fOto"
        const val PREMIUM = "fldl3EjSHWW0ZXg6w"
        const val STATUS = "fldQLRfw7xWBuwzAE" // Active / Expired / Lapsed
        const val DATE_OF_BIRTH = "fldxgJ1yGszXRfZUO"

        val FIELDS = listOf(
            CLIENT_NAME, MOBILE, POLICY_NUMBER, INSURANCE_COMPANY, POLICY_TYPE,
            END_DATE, PREMIUM, STATUS, DATE_OF_BIRTH,
        )
    }

    object FollowUps {
        const val TABLE = "tblsTTvDa0iL3cmRD"
        const val NAME = "Follow-ups"
        const val TASK = "fldp7P7VP5QNFsnMi"
        const val DUE_DATE = "fldNdtBOb4rCbnxKR"
        const val CLIENT_NAME = "fldX3VoAjgZOwpdGF"
        const val DONE = "fld8IrvULSrIzNUMv"

        val FIELDS = listOf(TASK, DUE_DATE, CLIENT_NAME, DONE)
    }

    object Leads {
        const val TABLE = "tblnR2gB0d59nK0m5"
        const val NAME = "Leads"
        const val LEAD_NAME = "fld69pjl4dhiDN044"
        const val MOBILE = "fldqoturGY5Ch34i9"
        const val INTERESTED_IN = "fldUYDaGWjua2x5GQ"
        const val STAGE = "fldUffjS7x6K4dNeG" // New / Contacted / Quote sent / Negotiating / Won / Lost
        const val NEXT_FOLLOW_UP = "fldBuu1rIMAeHkH37"

        val FIELDS = listOf(LEAD_NAME, MOBILE, INTERESTED_IN, STAGE, NEXT_FOLLOW_UP)
    }

    object Claims {
        const val TABLE = "tblotWd9ggeX5guds"
        const val NAME = "Claims"
        const val CLIENT_NAME = "fldLtHPdSperKqO0N"
        const val POLICY_NUMBER = "fldNyulQ6bZJqyQ5Z"
        const val CLAIM_NUMBER = "fldD5W79uA1SDWM7O"
        const val STATUS = "fldxXwJtgArSrTnzJ" // Intimated / Documents pending / Under review / Approved / Settled / Rejected

        val FIELDS = listOf(CLIENT_NAME, POLICY_NUMBER, CLAIM_NUMBER, STATUS)
    }

    object Payments {
        const val TABLE = "tbl1yeAijE1BUXcc2"
        const val NAME = "Payments"
        const val CLIENT_NAME = "fldFjHBjjASDy1ylK"
        const val POLICY_NUMBER = "fldFUx4iYCxDZyY2P"
        const val PAYMENT_TYPE = "fldHagC7i5NAl7lVq" // Premium from client / Commission from insurer
        const val AMOUNT = "fldOVhWKO0k3i6Ppn"
        const val DUE_DATE = "fld4EULzNgNwnlZUn"
        const val PAID_DATE = "fldVOAlLV9yGdM9oi"

        val FIELDS = listOf(CLIENT_NAME, POLICY_NUMBER, PAYMENT_TYPE, AMOUNT, DUE_DATE, PAID_DATE)
    }

    /** Every table and the fields the brief reads, for the "Check connection" schema test. */
    val REQUIRED: Map<String, Pair<String, List<String>>> = mapOf(
        Policies.TABLE to (Policies.NAME to Policies.FIELDS),
        FollowUps.TABLE to (FollowUps.NAME to FollowUps.FIELDS),
        Leads.TABLE to (Leads.NAME to Leads.FIELDS),
        Claims.TABLE to (Claims.NAME to Claims.FIELDS),
        Payments.TABLE to (Payments.NAME to Payments.FIELDS),
    )
}
