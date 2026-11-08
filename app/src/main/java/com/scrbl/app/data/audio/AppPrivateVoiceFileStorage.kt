package com.scrbl.app.data.audio

import com.scrbl.app.core.result.AppResult
import com.scrbl.app.core.result.FailureReason
import com.scrbl.app.domain.repository.VoiceFileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages app-private voice storage (`files/recalls/audio/<uuid>.m4a`) and temporary capture files
 * (`cache/recording_temp/<session>.tmp`) per 09_DATA_MODEL_AND_STORAGE.md section 6:
 * - Persists only relative paths (`recalls/audio/<uuid>.m4a`), never absolute device paths.
 * - Uses atomic move from temporary to permanent path, falling back to replace-existing copy+delete.
 * - Deletes uncommitted permanent files on Room insert failure (rollback).
 * - Cleans up abandoned `.tmp` files on startup without ever touching committed files.
 */
class AppPrivateVoiceFileStorage(
    private val appFilesDir: File,
    private val appCacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VoiceFileStorage {

    companion object {
        const val PERMANENT_SUBDIR = "recalls/audio"
        const val TEMP_SUBDIR = "recording_temp"
    }

    private val permanentDir: File
        get() = File(appFilesDir, PERMANENT_SUBDIR).apply { mkdirs() }

    private val tempDir: File
        get() = File(appCacheDir, TEMP_SUBDIR).apply { mkdirs() }

    override fun createTempRecordingFile(sessionId: String): File {
        val safeSession = sessionId.replace(Regex("[^a-zA-Z0-9_-]"), "")
        return File(tempDir, "capture_${safeSession}.tmp")
    }

    override fun resolvePermanentFile(relativePath: String): File {
        val cleanRelative = relativePath.trimStart('/')
        return File(appFilesDir, cleanRelative)
    }

    override fun existsAndNonEmpty(relativePath: String): Boolean {
        if (relativePath.isBlank() || relativePath.contains("..")) return false
        val target = resolvePermanentFile(relativePath)
        return target.exists() && target.isFile && target.length() > 0L
    }

    override suspend fun promoteTempToPermanent(tempFile: File, recallId: String): AppResult<String> =
        withContext(ioDispatcher) {
            try {
                if (!tempFile.exists() || tempFile.length() <= 0L) {
                    return@withContext AppResult.Error(
                        FailureReason.VOICE_FILE_MISSING_OR_EMPTY,
                        "Temporary recording file is missing or empty."
                    )
                }
                val safeId = recallId.replace(Regex("[^a-zA-Z0-9_-]"), "")
                val relativePath = "$PERMANENT_SUBDIR/$safeId.m4a"
                val destFile = resolvePermanentFile(relativePath)
                destFile.parentFile?.mkdirs()

                try {
                    Files.move(
                        tempFile.toPath(),
                        destFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: Exception) {
                    Files.move(
                        tempFile.toPath(),
                        destFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }

                if (!destFile.exists() || destFile.length() <= 0L) {
                    return@withContext AppResult.Error(
                        FailureReason.STORAGE_ERROR,
                        "Permanent voice file could not be verified after move."
                    )
                }
                AppResult.Success(relativePath)
            } catch (e: Exception) {
                AppResult.Error(
                    FailureReason.STORAGE_ERROR,
                    e.message ?: "Failed to move temporary audio file to app-private storage."
                )
            }
        }

    override suspend fun deleteUncommittedFile(relativePath: String) = withContext(ioDispatcher) {
        if (relativePath.isBlank() || relativePath.contains("..")) return@withContext
        runCatching {
            val file = resolvePermanentFile(relativePath)
            if (file.exists()) {
                file.delete()
            }
        }
        Unit
    }

    override suspend fun deleteTempFile(tempFile: File?) = withContext(ioDispatcher) {
        if (tempFile == null) return@withContext
        runCatching {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        Unit
    }

    override suspend fun cleanupAbandonedTempFiles(): Int = withContext(ioDispatcher) {
        var deletedCount = 0
        val dir = tempDir
        val files = dir.listFiles() ?: return@withContext 0
        for (file in files) {
            if (file.isFile && file.name.endsWith(".tmp")) {
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    deletedCount++
                }
            }
        }
        deletedCount
    }
}
