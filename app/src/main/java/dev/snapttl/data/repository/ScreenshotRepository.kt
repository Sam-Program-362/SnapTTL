package dev.snapttl.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import dev.snapttl.data.prefs.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DeletionOutcome { DELETED, FAILED }

/**
 * MediaStore screenshot discovery plus dual-method deletion (persistent SAF tree
 * first, MediaStore fallback), reconstructed from Mark's capture pipeline.
 */
class ScreenshotRepository(
    private val context: Context,
    private val prefs: PrefsManager
) {
    private val resolver: ContentResolver = context.contentResolver

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_TAKEN
    )

    suspend fun queryScreenshots(): List<ScreenshotMedia> = withContext(Dispatchers.IO) {
        val items = mutableListOf<ScreenshotMedia>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1) ?: ""
                val path = cursor.getString(2) ?: ""
                if (!ScreenshotMedia.isScreenshotName(name) && !ScreenshotMedia.isScreenshotPath(path)) continue
                val size = cursor.getLong(3)
                val dateTaken = cursor.getLong(4)
                val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    .buildUpon().appendPath(id.toString()).build().toString()
                items.add(ScreenshotMedia(id, name, path, uri, size, dateTaken))
            }
        }
        items
    }

    suspend fun findScreenshot(screenshotId: Long): ScreenshotMedia? = withContext(Dispatchers.IO) {
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media._ID}=?",
            arrayOf(screenshotId.toString()),
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            val id = cursor.getLong(0)
            val name = cursor.getString(1) ?: ""
            val path = cursor.getString(2) ?: ""
            val size = cursor.getLong(3)
            val dateTaken = cursor.getLong(4)
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                .buildUpon().appendPath(id.toString()).build().toString()
            ScreenshotMedia(id, name, path, uri, size, dateTaken)
        }
    }

    suspend fun deleteScreenshot(item: ScreenshotMedia): DeletionOutcome = withContext(Dispatchers.IO) {
        val treeUri = prefs.screenshotFolderTreeUri?.let { Uri.parse(it) }
        if (treeUri != null) {
            try {
                if (deleteViaSaf(item, treeUri)) return@withContext DeletionOutcome.DELETED
            } catch (_: Exception) {
                // fall through to MediaStore
            }
        }
        try {
            val deleted = resolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media._ID}=?",
                arrayOf(item.id.toString())
            )
            if (deleted > 0) DeletionOutcome.DELETED else DeletionOutcome.FAILED
        } catch (_: Exception) {
            DeletionOutcome.FAILED
        }
    }

    private fun deleteViaSaf(item: ScreenshotMedia, treeUri: Uri): Boolean {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        var docUri: Uri? = null
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
            arrayOf(item.displayName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
            }
        } ?: return false
        val document = docUri ?: return false
        return DocumentsContract.deleteDocument(resolver, document)
    }
}
