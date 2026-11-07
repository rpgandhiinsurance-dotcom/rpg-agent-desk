package com.rpginsurance.desk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rpginsurance.brief.Brief
import com.rpginsurance.brief.BriefItem
import com.rpginsurance.brief.BriefSection
import com.rpginsurance.brief.BriefText
import com.rpginsurance.brief.IST
import com.rpginsurance.brief.SectionKind
import com.rpginsurance.brief.TableFailure
import com.rpginsurance.desk.ContactActions
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefScreen(state: BriefUiState, onRefresh: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val brief = state.brief
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Daily brief")
                        Text(
                            subtitle(brief),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = state.hasToken && !state.refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    if (brief != null) {
                        IconButton(onClick = { ContactActions.share(context, BriefText.render(brief)) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share brief")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.hasToken) {
                    item { NoTokenCard(onOpenSettings) }
                    return@LazyColumn
                }
                if (state.refreshFailures.isNotEmpty()) {
                    item { FailureCard(state.refreshFailures, title = if (brief != null) "Couldn't refresh; showing the saved brief" else "Couldn't load the brief") }
                }
                if (brief == null) {
                    if (state.refreshFailures.isEmpty()) item { Centered("Loading…") }
                    return@LazyColumn
                }
                if (state.isStale) {
                    item { Banner("This brief is from ${formatDay(brief.date)}. Pull down to refresh.") }
                }
                if (brief.failures.isNotEmpty()) {
                    item { FailureCard(brief.failures, title = "Some tables didn't load") }
                }
                if (brief.nothingDue) {
                    item { NothingDue() }
                } else if (brief.sections.isEmpty()) {
                    item { Centered("Nothing due in the tables that loaded.") }
                }
                items(brief.sections, key = { it.kind.name }) { SectionCard(it) }
            }
        }
    }
}

@Composable
private fun SectionCard(section: BriefSection) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(section.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(section.kind.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${section.count}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            section.summary?.let {
                Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
            }
            section.items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ItemRow(item)
            }
        }
    }
}

@Composable
private fun ItemRow(item: BriefItem) {
    val context = LocalContext.current
    val table = item.airtableTableId
    val record = item.airtableRecordId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = table != null && record != null) {
                ContactActions.openInAirtable(context, table!!, record!!)
            }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (item.overdue) Badge("Overdue", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                if (item.noMobile) Badge("No mobile", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val mobile = item.mobile
        if (mobile != null) {
            IconButton(onClick = { ContactActions.dial(context, mobile) }) {
                Icon(Icons.Filled.Call, contentDescription = "Call ${item.name}")
            }
            IconButton(onClick = { ContactActions.whatsapp(context, mobile) }) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "WhatsApp ${item.name}")
            }
        }
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun FailureCard(failures: List<TableFailure>, title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            failures.forEach { Text("${it.table}: ${it.message}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun NoTokenCard(onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connect Airtable", style = MaterialTheme.typography.titleMedium)
            Text("Add an Airtable personal access token with read-only access to the RPG base to see your brief.")
            Button(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

@Composable
private fun NothingDue() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text(BriefText.NOTHING_DUE, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Banner(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { Text(text) }
}

private fun SectionKind.icon(): ImageVector = when (this) {
    SectionKind.BIRTHDAYS -> Icons.Filled.Cake
    SectionKind.RENEWALS -> Icons.Filled.Autorenew
    SectionKind.FOLLOW_UPS -> Icons.Filled.TaskAlt
    SectionKind.LEADS -> Icons.Filled.PersonSearch
    SectionKind.PAYMENTS -> Icons.Filled.Payments
    SectionKind.CLAIMS -> Icons.Filled.Description
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

private fun formatDay(iso: String) = LocalDate.parse(iso).format(dayFmt)

private fun subtitle(brief: Brief?): String {
    if (brief == null) return LocalDate.now(IST).format(dayFmt)
    val at = runCatching { Instant.parse(brief.generatedAt).atZone(IST).format(timeFmt) }.getOrNull()
    return formatDay(brief.date) + (at?.let { " · updated $it" } ?: "")
}
