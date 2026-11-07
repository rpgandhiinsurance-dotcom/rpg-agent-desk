package com.rpginsurance.brief

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BriefBuilderTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun policy(
        name: String,
        end: LocalDate? = null,
        status: String? = "Active",
        mobile: String? = "9876543210",
        dob: LocalDate? = null,
        id: String = "rec$name",
    ) = Policy(id, name, mobile, "P-$name", "HDFC Ergo", "Motor", end, 12345.0, status, dob)

    @Test fun `renewals hit exact milestones and overdue, skip others`() {
        val policies = listOf(30, 29, 15, 7, 3, 1, 0, -2, 31).map { policy("C$it", end = today.plusDays(it.toLong())) } +
            policy("Expired", end = today.minusDays(5), status = "Expired") +
            policy("Lapsed", end = today, status = "Lapsed")
        val s = BriefBuilder.renewalsSection(policies, today)!!
        assertEquals(listOf("C-2", "C0", "C1", "C3", "C7", "C15", "C30"), s.items.map { it.name })
        assertTrue(s.items.first().overdue)
        assertEquals("Motor · HDFC Ergo · ₹12,345 · #P-C-2 · expired 5 Nov (2 days ago)", s.items.first().detail)
        assertEquals("Motor · HDFC Ergo · ₹12,345 · #P-C30 · ends 7 Dec (30 days)", s.items.last().detail)
    }

    @Test fun `renewal without mobile is flagged`() {
        val s = BriefBuilder.renewalsSection(listOf(policy("A", end = today, mobile = null)), today)!!
        assertTrue(s.items.single().noMobile)
        assertEquals("A: Motor · HDFC Ergo · ₹12,345 · #P-A · ends today [no mobile]", BriefText.line(s.items.single()))
    }

    @Test fun `birthdays within three days, deduplicated by name, with age`() {
        val policies = listOf(
            policy("Asha Patil", dob = LocalDate.of(1980, 11, 7), mobile = null, id = "r1"),
            policy(" asha  patil ", dob = LocalDate.of(1980, 11, 7), mobile = "9000000000", id = "r2"),
            policy("Ravi", dob = LocalDate.of(1990, 11, 10)),
            policy("TooLate", dob = LocalDate.of(1990, 11, 11)),
            policy("Past", dob = LocalDate.of(1990, 11, 6)),
            policy("NoDob"),
        )
        val s = BriefBuilder.birthdaysSection(policies, today)!!
        assertEquals(listOf("Asha Patil", "Ravi"), s.items.map { it.name })
        assertEquals("turns 46 today", s.items[0].detail)
        assertEquals("9000000000", s.items[0].mobile) // a mobile on any of their policies counts
        assertFalse(s.items[0].noMobile)
        assertEquals("turns 36 on 10 Nov (in 3 days)", s.items[1].detail)
    }

    @Test fun `birthday window crosses the year end`() {
        val dec30 = LocalDate.of(2026, 12, 30)
        val s = BriefBuilder.birthdaysSection(listOf(policy("NY", dob = LocalDate.of(2000, 1, 1), mobile = null)), dec30)!!
        assertEquals("turns 27 on 1 Jan 2027 (in 2 days)", s.items.single().detail)
        assertTrue(s.items.single().noMobile)
    }

    @Test fun `leap day birthday falls on 28 Feb in other years`() {
        val (on, days) = BriefBuilder.nextBirthday(LocalDate.of(1992, 2, 29), LocalDate.of(2027, 2, 27))
        assertEquals(LocalDate.of(2027, 2, 28), on)
        assertEquals(1, days)
    }

    @Test fun `follow-ups due today or earlier and not done`() {
        val f = listOf(
            FollowUp("1", "add policy 7330371113, the email was too large to read automatically", "Shrikant Kaware", LocalDate.of(2026, 11, 4), false),
            FollowUp("2", "call", "Done Person", LocalDate.of(2026, 11, 1), true),
            FollowUp("3", "later", "Future", today.plusDays(1), false),
            FollowUp("4", "no date", "Nobody", null, false),
            FollowUp("5", "today", "Today Person", today, false),
        )
        val s = BriefBuilder.followUpsSection(f, today)!!
        assertEquals(listOf("Shrikant Kaware", "Today Person"), s.items.map { it.name })
        assertTrue(s.items[0].overdue)
        assertFalse(s.items[1].overdue)
    }

    @Test fun `leads exclude won and lost`() {
        val l = listOf(
            Lead("1", "Open", "9", "Health", "Contacted", today),
            Lead("2", "Won", null, null, "Won", today),
            Lead("3", "Lost", null, null, "Lost", today.minusDays(3)),
            Lead("4", "NoStage", null, null, null, today.minusDays(1)),
            Lead("5", "Future", null, null, "New", today.plusDays(1)),
        )
        val s = BriefBuilder.leadsSection(l, today)!!
        assertEquals(listOf("NoStage", "Open"), s.items.map { it.name })
        assertEquals("Health · Contacted · follow up 7 Nov", s.items[1].detail)
    }

    @Test fun `payments unpaid and due`() {
        val p = listOf(
            Payment("1", "A", "X1", "Premium from client", 5000.5, today.minusDays(1), null),
            Payment("2", "B", null, null, 100.0, today, today),
            Payment("3", "C", null, null, 100.0, today.plusDays(2), null),
        )
        val s = BriefBuilder.paymentsSection(p, today)!!
        assertEquals("Premium from client · ₹5,000.50 · #X1 · due 6 Nov", s.items.single().detail)
    }

    @Test fun `claims count open and name documents pending`() {
        val c = listOf(
            Claim("1", "A", null, "C1", "Intimated"),
            Claim("2", "B", "P2", "C2", "Documents pending"),
            Claim("3", "C", null, null, "Settled"),
            Claim("4", "D", null, null, "Approved"),
        )
        val s = BriefBuilder.claimsSection(c)!!
        assertEquals(3, s.count)
        assertEquals("3 open claims; documents pending for:", s.summary)
        assertEquals(listOf("B"), s.items.map { it.name })
        assertNull(BriefBuilder.claimsSection(listOf(Claim("x", "Z", null, null, "Rejected"))))
    }

    @Test fun `example run of 7 Nov 2026`() {
        val ok = { name: String, rows: List<Any> -> Result.success(name to rows) }
        @Suppress("UNCHECKED_CAST")
        val brief = BriefService.assemble(
            today = today,
            generatedAt = Instant.parse("2026-11-07T02:30:00Z"),
            policies = ok("Policies", listOf(policy("Someone", end = today.plusDays(40)))) as Result<Pair<String, List<Policy>>>,
            followUps = Result.success(
                "Follow-ups" to listOf(
                    FollowUp("a", "add policy 7330371113, the email was too large to read automatically", "Shrikant Kaware", LocalDate.of(2026, 11, 4), false),
                    FollowUp("b", "add policy 7330387989, the email was too large to read automatically", "Narendra Panjabrao Tingne", LocalDate.of(2026, 11, 4), false),
                ),
            ),
            leads = Result.success("Leads" to emptyList()),
            payments = Result.success("Payments" to emptyList()),
            claims = Result.success("Claims" to emptyList()),
        )
        val expected = """
            Follow-ups due:
            - Narendra Panjabrao Tingne: add policy 7330387989, the email was too large to read automatically (due 4 Nov)
            - Shrikant Kaware: add policy 7330371113, the email was too large to read automatically (due 4 Nov)
        """.trimIndent()
        assertEquals(expected, BriefText.render(brief))
        assertEquals("2 follow-ups", BriefText.headline(brief))
    }

    @Test fun `nothing due only when every table loaded`() {
        val empty = BriefService.assemble(
            today, Instant.EPOCH,
            Result.success("P" to emptyList()), Result.success("F" to emptyList()),
            Result.success("L" to emptyList()), Result.success("Pay" to emptyList()), Result.success("C" to emptyList()),
        )
        assertTrue(empty.nothingDue)
        assertEquals("Nothing due today", BriefText.render(empty))

        val failed = BriefService.assemble(
            today, Instant.EPOCH,
            Result.failure(BriefService.TableLoadException("Policies (Combined)", "Airtable rejected the token (401). Check it in Settings.", RuntimeException())),
            Result.success("F" to emptyList()),
            Result.success("L" to emptyList()), Result.success("Pay" to emptyList()), Result.success("C" to emptyList()),
        )
        assertFalse(failed.nothingDue)
        val text = BriefText.render(failed)
        assertTrue(text.startsWith("⚠ Couldn't load Policies (Combined): Airtable rejected the token (401)"), text)
        assertFalse(text.contains("Nothing due today"))
    }

    @Test fun `brief survives a json round trip for the offline cache`() {
        val b = Brief("2026-11-07", "2026-11-07T02:30:00Z", listOf(BriefBuilder.claimsSection(listOf(Claim("1", "A", null, null, "Approved")))!!))
        assertEquals(b, Brief.fromJson(b.toJson()))
        assertEquals(1, Brief.fromJson(b.toJson()).sections.single().count)
    }
}
