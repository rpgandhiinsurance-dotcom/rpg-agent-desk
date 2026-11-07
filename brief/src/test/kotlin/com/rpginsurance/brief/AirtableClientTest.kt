package com.rpginsurance.brief

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.Clock
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AirtableClientTest {
    private val server = MockWebServer().apply { start() }
    private fun client(token: String? = "pat123") =
        AirtableClient({ token }, server.url("/"), retryDelayMs = 1)

    @AfterTest fun tearDown() = server.shutdown()

    @Test fun `pages through offset and sends field ids`() = runTest {
        server.enqueue(MockResponse().setBody("""{"records":[{"id":"rec1","fields":{"fldX3VoAjgZOwpdGF":"A"}}],"offset":"itr1/rec1"}"""))
        server.enqueue(MockResponse().setBody("""{"records":[{"id":"rec2","fields":{}}]}"""))

        val rows = client().listRecords(Schema.FollowUps.TABLE, Schema.FollowUps.FIELDS)
        assertEquals(listOf("rec1", "rec2"), rows.map { it.id })

        val first = server.takeRequest()
        assertEquals("GET", first.method)
        assertEquals("Bearer pat123", first.getHeader("Authorization"))
        val url = first.requestUrl!!
        assertEquals("/v0/${Schema.BASE_ID}/${Schema.FollowUps.TABLE}", url.encodedPath)
        assertEquals("true", url.queryParameter("returnFieldsByFieldId"))
        assertEquals(Schema.FollowUps.FIELDS, url.queryParameterValues("fields[]"))
        assertEquals("itr1/rec1", server.takeRequest().requestUrl!!.queryParameter("offset"))
    }

    @Test fun `retries after 429 then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody("""{"records":[]}"""))
        assertEquals(0, client().listRecords("tblX", emptyList()).size)
        assertEquals(2, server.requestCount)
    }

    @Test fun `401 gives a readable error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val e = assertFailsWith<AirtableException> { client().listRecords("tblX", emptyList()) }
        assertEquals(401, e.httpCode)
        assertTrue(e.message!!.contains("token"))
    }

    @Test fun `missing token fails without a request`() = runTest {
        assertFailsWith<AirtableException> { client(token = " ").listRecords("tblX", emptyList()) }
        assertEquals(0, server.requestCount)
    }

    @Test fun `one failing table is named, others still show`() = runTest {
        // Route by table id so parallel requests get the right answer.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path.endsWith(Schema.Claims.TABLE) -> MockResponse().setResponseCode(500)
                    path.endsWith(Schema.FollowUps.TABLE) -> MockResponse().setBody(
                        """{"records":[{"id":"r","fields":{"fldp7P7VP5QNFsnMi":"Call","fldX3VoAjgZOwpdGF":"Ann","fldNdtBOb4rCbnxKR":"2026-11-07"}}]}""",
                    )
                    else -> MockResponse().setBody("""{"records":[]}""")
                }
            }
        }
        val clock = Clock.fixed(Instant.parse("2026-11-06T20:00:00Z"), IST) // 01:30 on 7 Nov in IST
        val brief = BriefService(client(), clock).build()
        assertEquals("2026-11-07", brief.date)
        assertEquals(listOf("Claims"), brief.failures.map { it.table })
        assertEquals(SectionKind.FOLLOW_UPS, brief.sections.single().kind)
    }

    @Test fun `schema check reports missing fields`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"tables":[{"id":"${Schema.FollowUps.TABLE}","fields":[{"id":"${Schema.FollowUps.TASK}"}]}]}""",
            ),
        )
        val problems = client().checkSchema()
        assertTrue(problems.any { it.contains("\"Policies (Combined)\"") && it.contains("missing") })
        assertTrue(problems.any { it.contains(Schema.FollowUps.DONE) })
        assertEquals("/v0/meta/bases/${Schema.BASE_ID}/tables", server.takeRequest().requestUrl!!.encodedPath)
    }
}
