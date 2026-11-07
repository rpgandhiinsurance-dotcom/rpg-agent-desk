package com.rpginsurance.desk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rpginsurance.brief.AirtableException
import com.rpginsurance.desk.Notifications
import com.rpginsurance.desk.app
import com.rpginsurance.desk.work.DailyScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onRequestNotificationPermission: () -> Unit) {
    val context = LocalContext.current
    val settings = context.app.settings
    val repo = context.app.repository
    val scope = rememberCoroutineScope()

    var tokenInput by remember { mutableStateOf("") }
    var hasToken by remember { mutableStateOf(settings.hasToken) }
    var daily by remember { mutableStateOf(settings.dailyNotification) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Notifications.canPost(context) // picks up changes made in system settings
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Airtable token", style = MaterialTheme.typography.titleMedium)
            Text(
                "Create a personal access token at airtable.com/create/tokens with the scopes " +
                    "data.records:read and schema.bases:read, limited to the RPG Insurance base. " +
                    "It's stored encrypted on this phone only.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (hasToken) "A token is saved." else "No token saved yet.",
                color = if (hasToken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it.trim() },
                label = { Text(if (hasToken) "Replace token" else "Paste token (pat…)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = tokenInput.isNotBlank(),
                    onClick = {
                        settings.token = tokenInput
                        tokenInput = ""
                        hasToken = true
                        status = "Token saved."
                    },
                ) { Text("Save") }
                if (hasToken) {
                    TextButton(onClick = {
                        settings.token = null
                        hasToken = false
                        status = "Token removed."
                    }) { Text("Remove") }
                }
            }
            OutlinedButton(
                enabled = hasToken && !checking,
                onClick = {
                    checking = true
                    status = "Checking…"
                    scope.launch {
                        status = try {
                            val problems = repo.checkConnection()
                            if (problems.isEmpty()) "Connected. All tables and fields the brief needs are present."
                            else "Connected, but:\n" + problems.joinToString("\n") { "• $it" }
                        } catch (e: AirtableException) {
                            e.message
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            "Unexpected response from Airtable (${e.javaClass.simpleName})"
                        } finally {
                            checking = false
                        }
                    }
                },
            ) { Text("Check connection") }
            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            HorizontalDivider()

            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily brief at 8:00 AM IST")
                    Text(
                        "Refreshes from Airtable and notifies you. Android may delay it a little to save battery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = daily,
                    onCheckedChange = { on ->
                        daily = on
                        settings.dailyNotification = on
                        if (on) {
                            DailyScheduler.reschedule(context)
                            onRequestNotificationPermission()
                        } else {
                            DailyScheduler.cancel(context)
                        }
                    },
                )
            }
            if (!canNotify) {
                Text("Notifications are turned off for this app.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRequestNotificationPermission) { Text("Allow notifications") }
            }
            OutlinedButton(
                enabled = hasToken,
                onClick = {
                    scope.launch {
                        val brief = repo.refresh()
                        Notifications.showBrief(context, brief)
                    }
                },
            ) { Text("Send today's brief as a notification now") }

            HorizontalDivider()
            Text(
                "This app only reads from Airtable. It never changes records and never messages clients; " +
                    "Call and WhatsApp just open those apps for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
