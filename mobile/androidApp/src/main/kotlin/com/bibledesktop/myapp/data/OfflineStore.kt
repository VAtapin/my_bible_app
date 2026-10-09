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
        private val packageCodes = mutableMapOf<String, Set<String>>()
        private const val maxRecord = 5 * 1024 * 1024
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
        if (key.startsWith("bible-package:")) lock.withLock {
            packageCodes[root.absolutePath]?.let { packageCodes[root.absolutePath] = it + key.removePrefix("bible-package:") }
        }
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

    /** Discover old and new packages without an index migration or reliance on the current server catalogue. */
    suspend fun biblePackages(): List<com.bibledesktop.shared.api.BiblePackage> = withContext(Dispatchers.IO) {
        lock.withLock {
            packageCodes[root.absolutePath]?.let { codes ->
                return@withLock codes.mapNotNull { code ->
                    runCatching {
                        val envelope = json.parseToJsonElement(AtomicFile(file(biblePackageKey(code), ".json")).readFully().decodeToString()).jsonObject
                        check(envelope["schemaVersion"]?.jsonPrimitive?.int == 1)
                        json.decodeFromJsonElement(com.bibledesktop.shared.api.BiblePackage.serializer(), envelope.getValue("data"))
                            .takeIf { it.translation.code == code }
                    }.getOrNull()
                }.sortedBy { it.translation.name }
            }
            root.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { target ->
                runCatching {
                    val bytes = AtomicFile(target).readFully()
                    check(bytes.size <= maxRecord)
                    val raw = bytes.decodeToString()
                    if (!raw.contains("\"books\":")) return@runCatching null
                    val envelope = json.parseToJsonElement(raw).jsonObject
                    if (envelope["schemaVersion"]?.jsonPrimitive?.int != 1) return@runCatching null
                    val data = envelope["data"] as? JsonObject ?: return@runCatching null
                    if (!data.containsKey("translation") || !data.containsKey("books")) return@runCatching null
                    json.decodeFromJsonElement(com.bibledesktop.shared.api.BiblePackage.serializer(), data)
                        .takeIf { target.name == file(biblePackageKey(it.translation.code), ".json").name }
                }.getOrNull()
            }.sortedBy { it.translation.name }.also { packages -> packageCodes[root.absolutePath] = packages.map { it.translation.code }.toSet() }
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
            // Keep room for atomic replacement and the OS; multiple installed Bibles have no fixed 256 MiB quota.
            check(root.usableSpace >= bytes.size + 16L * 1024 * 1024) { "Not enough free space for offline content" }
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try { stream.write(bytes); atomic.finishWrite(stream) }
            catch (error: Throwable) { atomic.failWrite(stream); throw error }
        }
    }
}

private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
