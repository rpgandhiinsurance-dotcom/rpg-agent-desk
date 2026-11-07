package com.rpginsurance.brief

import java.text.NumberFormat
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val IST: ZoneId = ZoneId.of("Asia/Kolkata")

/**
 * Pure brief rules. Each `*Section` takes already-loaded records plus today's IST date
 * and returns a section, or null when there's nothing to show.
 */
object BriefBuilder {
    val RENEWAL_DAYS = setOf(30L, 15L, 7L, 3L, 1L, 0L)
    const val BIRTHDAY_WINDOW_DAYS = 3L
    val OPEN_CLAIM_STATUSES = setOf("Intimated", "Documents pending", "Under review", "Approved")
    const val DOCUMENTS_PENDING = "Documents pending"
    private val CLOSED_LEAD_STAGES = setOf("won", "lost")

    // 1. Birthdays ---------------------------------------------------------------------------
    fun birthdaysSection(policies: List<Policy>, today: LocalDate): BriefSection? {
        data class Hit(val name: String, val days: Long, val on: LocalDate, val age: Int, val mobile: String?, val id: String)

        // Group by client so someone with three policies is listed once. A mobile on any of
        // their policies counts; the first DOB found wins.
        val hits = policies
            .filter { it.dateOfBirth != null }
            .groupBy { normaliseName(it.clientName) }
            .mapNotNull { (_, group) ->
                val p = group.first()
                val dob = p.dateOfBirth!!
                val (on, days) = nextBirthday(dob, today)
                if (days > BIRTHDAY_WINDOW_DAYS) return@mapNotNull null
                Hit(
                    name = p.clientName,
                    days = days,
                    on = on,
                    age = on.year - dob.year,
                    mobile = group.firstNotNullOfOrNull { it.mobile },
                    id = p.id,
                )
            }
            .sortedWith(compareBy({ it.days }, { it.name }))

        if (hits.isEmpty()) return null
        return BriefSection(SectionKind.BIRTHDAYS, hits.map { h ->
            val turning = if (h.age > 0) "turns ${h.age}" else "birthday"
            val whenText = when (h.days) {
                0L -> "today"
                1L -> "tomorrow"
                else -> "on ${shortDate(h.on, today)} (in ${h.days} days)"
            }
            BriefItem(
                name = h.name,
                detail = "$turning $whenText",
                mobile = h.mobile,
                noMobile = h.mobile == null,
                airtableTableId = Schema.Policies.TABLE,
                airtableRecordId = h.id,
            )
        })
    }

    /** Next occurrence of [dob]'s day and month on or after [today]. 29 Feb falls on 28 Feb in other years. */
    internal fun nextBirthday(dob: LocalDate, today: LocalDate): Pair<LocalDate, Long> {
        fun inYear(y: Int): LocalDate = MonthDay.from(dob).let { md ->
            if (md.isValidYear(y)) md.atYear(y) else LocalDate.of(y, 2, 28)
        }
        var next = inYear(today.year)
        if (next.isBefore(today)) next = inYear(today.year + 1)
        return next to ChronoUnit.DAYS.between(today, next)
    }

    // 2. Renewals ----------------------------------------------------------------------------
    fun renewalsSection(policies: List<Policy>, today: LocalDate): BriefSection? {
        val hits = policies
            .filter { it.status.equals("Active", ignoreCase = true) && it.endDate != null }
            .map { it to ChronoUnit.DAYS.between(today, it.endDate) }
            .filter { (_, days) -> days < 0 || days in RENEWAL_DAYS }
            .sortedWith(compareBy({ it.second }, { it.first.clientName }))

        if (hits.isEmpty()) return null
        return BriefSection(SectionKind.RENEWALS, hits.map { (p, days) ->
            val ends = shortDate(p.endDate!!, today)
            val whenText = when {
                days < 0 -> "expired $ends (${-days} ${plural(-days, "day")} ago)"
                days == 0L -> "ends today"
                days == 1L -> "ends tomorrow"
                else -> "ends $ends ($days days)"
            }
            val parts = listOfNotNull(p.policyType, p.insurer, p.premium?.let(::rupees), p.policyNumber?.let { "#$it" })
            BriefItem(
                name = p.clientName,
                detail = (parts + whenText).joinToString(" · "),
                mobile = p.mobile,
                noMobile = p.mobile == null,
                overdue = days < 0,
                airtableTableId = Schema.Policies.TABLE,
                airtableRecordId = p.id,
            )
        })
    }

    // 3. Follow-ups --------------------------------------------------------------------------
    fun followUpsSection(followUps: List<FollowUp>, today: LocalDate): BriefSection? {
        val due = followUps
            .filter { !it.done && it.dueDate != null && !it.dueDate.isAfter(today) }
            .sortedWith(compareBy({ it.dueDate }, { it.clientName.orEmpty() }))
        if (due.isEmpty()) return null
        return BriefSection(SectionKind.FOLLOW_UPS, due.map { f ->
            val dueText = "due ${shortDate(f.dueDate!!, today)}"
            val name = f.clientName ?: f.task ?: "(untitled task)"
            val detail = if (f.clientName != null && f.task != null) "${f.task} ($dueText)" else dueText
            BriefItem(
                name = name,
                detail = detail,
                overdue = f.dueDate.isBefore(today),
                airtableTableId = Schema.FollowUps.TABLE,
                airtableRecordId = f.id,
            )
        })
    }

    // 4. Leads -------------------------------------------------------------------------------
    fun leadsSection(leads: List<Lead>, today: LocalDate): BriefSection? {
        val due = leads
            .filter { it.stage?.trim()?.lowercase() !in CLOSED_LEAD_STAGES }
            .filter { it.nextFollowUp != null && !it.nextFollowUp.isAfter(today) }
            .sortedWith(compareBy({ it.nextFollowUp }, { it.name.orEmpty() }))
        if (due.isEmpty()) return null
        return BriefSection(SectionKind.LEADS, due.map { l ->
            val parts = listOfNotNull(l.interestedIn, l.stage, "follow up ${shortDate(l.nextFollowUp!!, today)}")
            BriefItem(
                name = l.name ?: "(no name)",
                detail = parts.joinToString(" · "),
                mobile = l.mobile,
                overdue = l.nextFollowUp.isBefore(today),
                airtableTableId = Schema.Leads.TABLE,
                airtableRecordId = l.id,
            )
        })
    }

    // 5. Payments ----------------------------------------------------------------------------
    fun paymentsSection(payments: List<Payment>, today: LocalDate): BriefSection? {
        val due = payments
            .filter { it.paidDate == null && it.dueDate != null && !it.dueDate.isAfter(today) }
            .sortedWith(compareBy({ it.dueDate }, { it.clientName.orEmpty() }))
        if (due.isEmpty()) return null
        return BriefSection(SectionKind.PAYMENTS, due.map { p ->
            val parts = listOfNotNull(
                p.paymentType,
                p.amount?.let(::rupees),
                p.policyNumber?.let { "#$it" },
                "due ${shortDate(p.dueDate!!, today)}",
            )
            BriefItem(
                name = p.clientName ?: "(no client)",
                detail = parts.joinToString(" · "),
                overdue = p.dueDate.isBefore(today),
                airtableTableId = Schema.Payments.TABLE,
                airtableRecordId = p.id,
            )
        })
    }

    // 6. Claims ------------------------------------------------------------------------------
    fun claimsSection(claims: List<Claim>): BriefSection? {
        val open = claims.filter { it.status in OPEN_CLAIM_STATUSES }
        if (open.isEmpty()) return null
        val pending = open.filter { it.status == DOCUMENTS_PENDING }.sortedBy { it.clientName.orEmpty() }
        val summary = buildString {
            append("${open.size} open ${plural(open.size.toLong(), "claim")}")
            if (pending.isNotEmpty()) append("; documents pending for:")
        }
        return BriefSection(
            kind = SectionKind.CLAIMS,
            summary = summary,
            count = open.size,
            items = pending.map { c ->
                BriefItem(
                    name = c.clientName ?: "(no client)",
                    detail = listOfNotNull(
                        c.claimNumber?.let { "claim $it" },
                        c.policyNumber?.let { "policy #$it" },
                    ).joinToString(" · ").ifEmpty { "documents pending" },
                    airtableTableId = Schema.Claims.TABLE,
                    airtableRecordId = c.id,
                )
            },
        )
    }

    // Formatting helpers -----------------------------------------------------------------------
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val longFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun shortDate(d: LocalDate, today: LocalDate): String =
        if (d.year == today.year) d.format(shortFmt) else d.format(longFmt)

    /** Indian grouping, e.g. ₹1,23,456 (paise only when non-zero). */
    fun rupees(amount: Double): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        nf.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        nf.minimumFractionDigits = nf.maximumFractionDigits
        return nf.format(amount)
    }

    private fun plural(n: Long, word: String) = if (n == 1L) word else "${word}s"

    internal fun normaliseName(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase()
}
