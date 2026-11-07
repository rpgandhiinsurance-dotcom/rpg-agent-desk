package com.rpginsurance.desk.data

import android.content.Context
import com.rpginsurance.brief.AirtableClient
import com.rpginsurance.brief.Brief
import com.rpginsurance.brief.BriefService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetches the brief from Airtable and keeps the last good copy on disk, so the app opens
 * instantly and still shows something offline.
 */
class BriefRepository(context: Context, private val settings: Settings) {
    private val client = AirtableClient(token = { settings.token })
    private val service = BriefService(client)
    private val cacheFile = File(context.noBackupFilesDir, "last_brief.json")
    private val mutex = Mutex()

    private val _brief = MutableStateFlow(readCache())
    /** The latest brief: from disk at start-up, then replaced after each refresh. */
    val brief: StateFlow<Brief?> = _brief.asStateFlow()

    /**
     * Rebuilds the brief from Airtable. If every table fails (offline, bad token) the cached
     * brief is kept and the returned brief carries the failures, so the UI can explain.
     */
    suspend fun refresh(): Brief = mutex.withLock {
        val fresh = service.build()
        val allFailed = fresh.failures.size >= TABLE_COUNT
        if (!allFailed) {
            _brief.value = fresh
            withContext(Dispatchers.IO) {
                val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
                tmp.writeText(fresh.toJson())
                tmp.renameTo(cacheFile)
            }
        }
        fresh
    }

    suspend fun checkConnection(): List<String> = client.checkSchema()

    private fun readCache(): Brief? = try {
        cacheFile.takeIf { it.exists() }?.readText()?.let(Brief::fromJson)
    } catch (_: Exception) {
        null // a cache from an older app version that no longer parses is simply ignored
    }

    private companion object {
        const val TABLE_COUNT = 5
    }
}
