package com.rpginsurance.brief

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class Policy(
    val id: String,
    val clientName: String,
    val mobile: String?,
    val policyNumber: String?,
    val insurer: String?,
    val policyType: String?,
    val endDate: LocalDate?,
    val premium: Double?,
    val status: String?,
    val dateOfBirth: LocalDate?,
)

data class FollowUp(
    val id: String,
    val task: String?,
    val clientName: String?,
    val dueDate: LocalDate?,
    val done: Boolean,
)

data class Lead(
    val id: String,
    val name: String?,
    val mobile: String?,
    val interestedIn: String?,
    val stage: String?,
    val nextFollowUp: LocalDate?,
)

data class Claim(
    val id: String,
    val clientName: String?,
    val policyNumber: String?,
    val claimNumber: String?,
    val status: String?,
)

data class Payment(
    val id: String,
    val clientName: String?,
    val policyNumber: String?,
    val paymentType: String?,
    val amount: Double?,
    val dueDate: LocalDate?,
    val paidDate: LocalDate?,
)

/** One Airtable record as returned with returnFieldsByFieldId=true. */
data class RawRecord(val id: String, val fields: JsonObject) {
    fun text(fieldId: String): String? = (fields[fieldId] as? JsonPrimitive)
        ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    fun number(fieldId: String): Double? = (fields[fieldId] as? JsonPrimitive)?.doubleOrNull

    fun checkbox(fieldId: String): Boolean = (fields[fieldId] as? JsonPrimitive)?.booleanOrNull ?: false

    /** Airtable date fields come back as "YYYY-MM-DD"; a malformed value is treated as empty. */
    fun date(fieldId: String): LocalDate? = text(fieldId)?.let {
        try {
            LocalDate.parse(it.take(10))
        } catch (_: DateTimeParseException) {
            null
        }
    }
}


object Mappers {
    fun policy(r: RawRecord) = with(Schema.Policies) {
        Policy(
            id = r.id,
            clientName = r.text(CLIENT_NAME) ?: "(no name)",
            mobile = r.text(MOBILE),
            policyNumber = r.text(POLICY_NUMBER),
            insurer = r.text(INSURANCE_COMPANY),
            policyType = r.text(POLICY_TYPE),
            endDate = r.date(END_DATE),
            premium = r.number(PREMIUM),
            status = r.text(STATUS),
            dateOfBirth = r.date(DATE_OF_BIRTH),
        )
    }

    fun followUp(r: RawRecord) = with(Schema.FollowUps) {
        FollowUp(r.id, r.text(TASK), r.text(CLIENT_NAME), r.date(DUE_DATE), r.checkbox(DONE))
    }

    fun lead(r: RawRecord) = with(Schema.Leads) {
        Lead(r.id, r.text(LEAD_NAME), r.text(MOBILE), r.text(INTERESTED_IN), r.text(STAGE), r.date(NEXT_FOLLOW_UP))
    }

    fun claim(r: RawRecord) = with(Schema.Claims) {
        Claim(r.id, r.text(CLIENT_NAME), r.text(POLICY_NUMBER), r.text(CLAIM_NUMBER), r.text(STATUS))
    }

    fun payment(r: RawRecord) = with(Schema.Payments) {
        Payment(
            r.id, r.text(CLIENT_NAME), r.text(POLICY_NUMBER), r.text(PAYMENT_TYPE),
            r.number(AMOUNT), r.date(DUE_DATE), r.date(PAID_DATE),
        )
    }
}
