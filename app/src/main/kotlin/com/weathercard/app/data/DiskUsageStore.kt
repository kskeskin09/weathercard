package com.weathercard.app.data

import android.util.AtomicFile
import com.weathercard.core.UsageLedger
import com.weathercard.core.UsageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

class DiskUsageStore(file: File) : UsageStore {
    private val target = AtomicFile(file)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): UsageLedger? = withContext(Dispatchers.IO) {
        try {
            target.openRead().bufferedReader().use { json.decodeFromString<UsageLedger>(it.readText()) }
        } catch (_: FileNotFoundException) { null }
    }

    override suspend fun write(ledger: UsageLedger): Unit = withContext(Dispatchers.IO) {
        val stream = target.startWrite()
        try {
            stream.write(json.encodeToString(ledger).toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }
}

