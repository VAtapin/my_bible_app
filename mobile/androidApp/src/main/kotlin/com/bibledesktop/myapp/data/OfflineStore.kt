package com.bibledesktop.myapp.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest

/** Durable content, outside Android's evictable cache and outside automatic cloud backup. */
internal class OfflineStore(private val root: File) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "offline-content-v1"))
    companion object {
        private val lock = Mutex()
        private const val maxRecord = 5 * 1024 * 1024
        private const val maxStorage = 256L * 1024 * 1024
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
    private fun file(key: String, extension: String) = File(root, digest(key) + extension)

    suspend fun <T> read(key: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        lock.withLock {
            val target = file(key, ".json")
            if (!target.exists() && !File(target.path + ".bak").exists()) return@withLock null
            runCatching {
                val bytes = AtomicFile(target).readFully()
                check(bytes.size <= maxRecord)
                val envelope = json.parseToJsonElement(bytes.decodeToString()).jsonObject
                check(envelope["schemaVersion"]?.jsonPrimitive?.int == 1)
                json.decodeFromJsonElement(serializer, envelope.getValue("data"))
            }.getOrNull()
        }
    }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        val envelope = buildJsonObject {
            put("schemaVersion", 1); put("savedAt", System.currentTimeMillis())
            put("data", json.encodeToJsonElement(serializer, value))
        }.toString().encodeToByteArray()
        require(envelope.size <= maxRecord)
        save(file(key, ".json"), envelope, protectSchema = true)
    }

    suspend fun savedAt(key: String): Long = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val bytes = AtomicFile(file(key, ".json")).readFully()
                check(bytes.size <= maxRecord)
                json.parseToJsonElement(bytes.decodeToString()).jsonObject["savedAt"]!!.jsonPrimitive.long
            }.getOrDefault(0L)
        }
    }

    suspend fun saveImage(url: String, bytes: ByteArray): File {
        require(bytes.isNotEmpty() && bytes.size <= 384 * 1024)
        val target = file(url, ".image")
        save(target, bytes, protectSchema = false)
        return target
    }

    suspend fun image(url: String): File? = withContext(Dispatchers.IO) {
        lock.withLock { file(url, ".image").takeIf { it.isFile && it.length() in 1..(384 * 1024L) } }
    }

    private suspend fun save(target: File, bytes: ByteArray, protectSchema: Boolean) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(root.isDirectory || root.mkdirs())
            if (protectSchema && target.exists()) {
                val schema = runCatching { json.parseToJsonElement(target.readText()).jsonObject["schemaVersion"]?.jsonPrimitive?.int }.getOrNull()
                check(schema == null || schema == 1) { "Unsupported local content schema" }
            }
            check(root.listFiles().orEmpty().sumOf { it.length() } - target.length() + bytes.size <= maxStorage) { "Offline storage limit reached" }
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try { stream.write(bytes); atomic.finishWrite(stream) }
            catch (error: Throwable) { atomic.failWrite(stream); throw error }
        }
    }
}

private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
