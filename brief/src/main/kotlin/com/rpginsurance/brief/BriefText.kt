package com.rpginsurance.brief

/** Plain-text rendering, used for the notification and the share button. */
object BriefText {
    const val NOTHING_DUE = "Nothing due today"

    fun render(brief: Brief): String = buildString {
        brief.failures.forEach { appendLine("⚠ Couldn't load ${it.table}: ${it.message}") }
        if (brief.failures.isNotEmpty()) appendLine()
        if (brief.nothingDue) {
            append(NOTHING_DUE)
            return@buildString
        }
        if (brief.sections.isEmpty()) {
            append("Nothing due in the tables that loaded.")
            return@buildString
        }
        brief.sections.forEachIndexed { i, s ->
            if (i > 0) appendLine()
            appendLine("${s.kind.title}:")
            s.summary?.let { appendLine(it) }
            s.items.forEach { appendLine("- ${line(it)}") }
        }
    }.trimEnd()

    fun line(item: BriefItem): String = buildString {
        append(item.name)
        append(": ")
        append(item.detail)
        if (item.noMobile) append(" [no mobile]")
    }

    /** One-line summary for the collapsed notification, e.g. "2 follow-ups · 1 renewal". */
    fun headline(brief: Brief): String {
        if (brief.nothingDue) return NOTHING_DUE
        val parts = brief.sections.map { s ->
            val n = s.count
            val noun = when (s.kind) {
                SectionKind.BIRTHDAYS -> "birthday"
                SectionKind.RENEWALS -> "renewal"
                SectionKind.FOLLOW_UPS -> "follow-up"
                SectionKind.LEADS -> "lead"
                SectionKind.PAYMENTS -> "payment"
                SectionKind.CLAIMS -> "open claim"
            }
            "$n $noun" + if (n == 1) "" else "s"
        }
        val failed = if (brief.failures.isNotEmpty()) listOf("${brief.failures.size} table(s) failed") else emptyList()
        return (failed + parts).joinToString(" · ").ifEmpty { "Nothing due in the tables that loaded" }
    }
}
