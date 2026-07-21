package com.tracker.quadrix.data

import android.content.Context
import android.util.Log
import com.tracker.quadrix.data.api.LocationPayload
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Durable FIFO queue of location fixes waiting to be uploaded.
 *
 * Firestore used to provide this for free; with a plain REST backend it has to be explicit.
 * Backed by a single JSON file rewritten on every change — at one fix per 5 minutes the file
 * stays tiny (a full [MAX_ENTRIES] queue is well under 200 KB), so the simplicity is worth
 * more than the write amplification a database would avoid.
 *
 * All access is synchronised: the service writes from the location callback while the upload
 * coroutine drains it.
 */
class LocationQueue(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    fun add(payload: LocationPayload) = synchronized(lock) {
        val entries = readAll().toMutableList()
        entries += payload
        // Drop the oldest fixes first: a long offline stretch should not cost us the recent
        // positions, which are the ones that still matter when the device reappears.
        val trimmed = if (entries.size > MAX_ENTRIES) {
            entries.takeLast(MAX_ENTRIES)
        } else {
            entries
        }
        writeAll(trimmed)
    }

    fun peek(limit: Int): List<LocationPayload> = synchronized(lock) {
        readAll().take(limit)
    }

    /** Removes the first [count] entries, after they have been accepted by the server. */
    fun removeFirst(count: Int) = synchronized(lock) {
        val remaining = readAll().drop(count)
        writeAll(remaining)
    }

    fun size(): Int = synchronized(lock) { readAll().size }

    fun clear() = synchronized(lock) {
        runCatching { file.delete() }
        Unit
    }

    private fun readAll(): List<LocationPayload> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer, file.readText())
        }.getOrElse { error ->
            // A truncated file (process killed mid-write) would otherwise wedge the queue
            // forever; losing the pending fixes beats never uploading again.
            Log.w(TAG, "Queue file unreadable, discarding", error)
            file.delete()
            emptyList()
        }
    }

    private fun writeAll(entries: List<LocationPayload>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer, entries))
        }.onFailure { error ->
            Log.e(TAG, "Failed to persist queue", error)
        }
    }

    private companion object {
        const val TAG = "LocationQueue"
        const val FILE_NAME = "pending_locations.json"

        /** ~3.5 days of fixes at one every 5 minutes. */
        const val MAX_ENTRIES = 1000

        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(
            LocationPayload.serializer()
        )
    }
}
