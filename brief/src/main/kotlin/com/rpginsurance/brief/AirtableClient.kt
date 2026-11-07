package com.rpginsurance.brief

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A request to Airtable failed; [message] is written for the person reading the brief. */
class AirtableException(message: String, val httpCode: Int? = null, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Read-only Airtable client. It only ever issues GET requests: the brief never creates,
 * changes or deletes a record.
 */
class AirtableClient(
    private val token: () -> String?,
    private val baseUrl: HttpUrl = "https://api.airtable.com/".toHttpUrl(),
    private val http: OkHttpClient = defaultHttp(),
    private val retryDelayMs: Long = 30_000,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Lists every record in [tableId], following `offset` across pages of 100. */
    suspend fun listRecords(
        tableId: String,
        fieldIds: List<String>,
        baseId: String = Schema.BASE_ID,
    ): List<RawRecord> {
        val out = mutableListOf<RawRecord>()
        var offset: String? = null
        do {
            val url = baseUrl.newBuilder()
                .addPathSegments("v0/$baseId/$tableId")
                .addQueryParameter("returnFieldsByFieldId", "true")
                .addQueryParameter("pageSize", "100")
                .apply {
                    fieldIds.forEach { addQueryParameter("fields[]", it) }
                    offset?.let { addQueryParameter("offset", it) }
                }
                .build()
            val body = get(url)
            val records = body["records"]?.jsonArray ?: JsonArray(emptyList())
            records.forEach { el ->
                val o = el.jsonObject
                out += RawRecord(
                    id = o["id"]!!.jsonPrimitive.content,
                    fields = o["fields"] as? JsonObject ?: JsonObject(emptyMap()),
                )
            }
            offset = (body["offset"] as? JsonPrimitive)?.content
        } while (offset != null)
        return out
    }

    /**
     * Checks that every table and field the brief depends on still exists.
     * Returns a list of problems; empty means the schema matches. Needs `schema.bases:read`.
     */
    suspend fun checkSchema(baseId: String = Schema.BASE_ID): List<String> {
        val url = baseUrl.newBuilder().addPathSegments("v0/meta/bases/$baseId/tables").build()
        val tables = get(url)["tables"]?.jsonArray.orEmpty().map { it.jsonObject }
        val byId = tables.associateBy { it["id"]!!.jsonPrimitive.content }
        val problems = mutableListOf<String>()
        for ((tableId, spec) in Schema.REQUIRED) {
            val (name, fields) = spec
            val table = byId[tableId]
            if (table == null) {
                problems += "Table \"$name\" ($tableId) is missing"
                continue
            }
            val have = table["fields"]?.jsonArray.orEmpty()
                .map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
            fields.filterNot { it in have }.forEach { problems += "Field $it is missing from \"$name\"" }
        }
        return problems
    }

    private suspend fun get(url: HttpUrl): JsonObject = withContext(Dispatchers.IO) { getBlocking(url) }

    private suspend fun getBlocking(url: HttpUrl): JsonObject {
        val pat = token()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: throw AirtableException("No Airtable token saved. Add one in Settings.")
        var attempt = 0
        while (true) {
            attempt++
            val request = Request.Builder().url(url).header("Authorization", "Bearer $pat").get().build()
            val (code, text) = try {
                http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw AirtableException("Network error: ${e.message ?: e.javaClass.simpleName}", cause = e)
            }
            if (code in 200..299) return json.parseToJsonElement(text).jsonObject
            // Airtable allows 5 requests/second per base and asks clients to wait 30s after a 429.
            if (code != 429 || attempt >= 3) throw AirtableException(describe(code), code)
            delay(retryDelayMs)
        }
    }

    private fun describe(code: Int) = when (code) {
        401 -> "Airtable rejected the token (401). Check it in Settings."
        403 -> "The token can't read this base (403). It needs data.records:read on this base."
        404 -> "Table not found (404). It may have been deleted or the token can't see it."
        422 -> "Airtable couldn't process the request (422). A field may have been deleted."
        429 -> "Airtable rate limit hit (429). Try again in a minute."
        in 500..599 -> "Airtable is having problems ($code). Try again later."
        else -> "Airtable returned HTTP $code."
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
