package com.rpginsurance.brief

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class SectionKind(val title: String) {
    BIRTHDAYS("Birthdays"),
    RENEWALS("Renewals"),
    FOLLOW_UPS("Follow-ups due"),
    LEADS("Leads to call"),
    PAYMENTS("Payments due"),
    CLAIMS("Open claims"),
}

@Serializable
data class BriefItem(
    val name: String,
    val detail: String,
    /** The client's mobile, if known. Used only for tap-to-call / WhatsApp opened by the user. */
    val mobile: String? = null,
    /** True when this section flags a missing mobile (birthdays and renewals). */
    val noMobile: Boolean = false,
    /** True for something already late, e.g. a renewal past its end date. */
    val overdue: Boolean = false,
    val airtableTableId: String? = null,
    val airtableRecordId: String? = null,
)

@Serializable
data class BriefSection(
    val kind: SectionKind,
    val items: List<BriefItem>,
    /** A line shown above the items, e.g. "3 open claims". */
    val summary: String? = null,
    /** How many things the section counts; differs from items.size for claims. */
    val count: Int = items.size,
)

@Serializable
data class TableFailure(val table: String, val message: String)

@Serializable
data class Brief(
    /** Today's date in Asia/Kolkata, ISO-8601. */
    val date: String,
    /** When the data was fetched, ISO-8601 instant. */
    val generatedAt: String,
    val sections: List<BriefSection>,
    val failures: List<TableFailure> = emptyList(),
) {
    /** "Nothing due today" is only true when every table loaded and every section is empty. */
    val nothingDue: Boolean get() = sections.isEmpty() && failures.isEmpty()

    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun fromJson(text: String): Brief = json.decodeFromString(serializer(), text)
    }
}
