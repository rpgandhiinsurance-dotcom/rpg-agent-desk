package com.rpginsurance.brief

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Loads the five tables and builds today's brief. A table that fails to load is reported in
 * [Brief.failures] instead of silently looking like "nothing due".
 */
class BriefService(
    private val client: AirtableClient,
    private val clock: Clock = Clock.system(IST),
) {
    suspend fun build(): Brief = coroutineScope {
        val today = LocalDate.now(clock.withZone(IST))

        // Airtable allows 5 requests/second per base; five tables in parallel stays within it
        // for small tables (each page is one request).
        val policies = async { load(Schema.Policies.NAME) { client.listRecords(Schema.Policies.TABLE, Schema.Policies.FIELDS).map(Mappers::policy) } }
        val followUps = async { load(Schema.FollowUps.NAME) { client.listRecords(Schema.FollowUps.TABLE, Schema.FollowUps.FIELDS).map(Mappers::followUp) } }
        val leads = async { load(Schema.Leads.NAME) { client.listRecords(Schema.Leads.TABLE, Schema.Leads.FIELDS).map(Mappers::lead) } }
        val payments = async { load(Schema.Payments.NAME) { client.listRecords(Schema.Payments.TABLE, Schema.Payments.FIELDS).map(Mappers::payment) } }
        val claims = async { load(Schema.Claims.NAME) { client.listRecords(Schema.Claims.TABLE, Schema.Claims.FIELDS).map(Mappers::claim) } }

        assemble(
            today = today,
            generatedAt = Instant.now(clock),
            policies = policies.await(),
            followUps = followUps.await(),
            leads = leads.await(),
            payments = payments.await(),
            claims = claims.await(),
        )
    }

    private suspend fun <T> load(table: String, block: suspend () -> List<T>): Result<Pair<String, List<T>>> =
        try {
            Result.success(table to block())
        } catch (e: AirtableException) {
            Result.failure(TableLoadException(table, e.message ?: "unknown error", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Malformed JSON or an unexpected shape; report it against this table only.
            Result.failure(TableLoadException(table, "Unexpected response from Airtable (${e.javaClass.simpleName})", e))
        }

    class TableLoadException(val table: String, message: String, cause: Throwable) : Exception(message, cause)

    companion object {
        /** Combines loaded tables into a brief, in the fixed section order. Pure; used by tests. */
        fun assemble(
            today: LocalDate,
            generatedAt: Instant,
            policies: Result<Pair<String, List<Policy>>>,
            followUps: Result<Pair<String, List<FollowUp>>>,
            leads: Result<Pair<String, List<Lead>>>,
            payments: Result<Pair<String, List<Payment>>>,
            claims: Result<Pair<String, List<Claim>>>,
        ): Brief {
            val failures = mutableListOf<TableFailure>()
            fun <T> Result<Pair<String, List<T>>>.rows(): List<T>? = fold(
                onSuccess = { it.second },
                onFailure = { e ->
                    val t = (e as? TableLoadException)?.table ?: "a table"
                    failures += TableFailure(t, e.message ?: "unknown error")
                    null
                },
            )

            val p = policies.rows()
            val f = followUps.rows()
            val l = leads.rows()
            val pay = payments.rows()
            val c = claims.rows()

            val sections = listOfNotNull(
                p?.let { BriefBuilder.birthdaysSection(it, today) },
                p?.let { BriefBuilder.renewalsSection(it, today) },
                f?.let { BriefBuilder.followUpsSection(it, today) },
                l?.let { BriefBuilder.leadsSection(it, today) },
                pay?.let { BriefBuilder.paymentsSection(it, today) },
                c?.let { BriefBuilder.claimsSection(it) },
            )
            return Brief(today.toString(), generatedAt.toString(), sections, failures)
        }
    }
}
