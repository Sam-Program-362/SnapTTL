package dev.snapttl.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import dev.snapttl.data.prefs.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class DeletionOutcome { DELETED, FAILED }

/**
 * MediaStore screenshot discovery plus dual-method deletion (persistent SAF tree
 * first, MediaStore fallback), reconstructed from Mark's capture pipeline.
 *
 * All queries funnel through [ScreenshotFilter]; anything it rejects (pending,
 * trashed, deleted, dot-prefixed, non-screenshot names) is invisible here.
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
                if (deleteViaSaf(item, treeUri)) {
                    // SAF reported success — trust but verify: only say "deleted"
                    // once MediaStore no longer returns the row. A lying toast
                    // is worse than a failure toast.
                    return@withContext if (waitForMediaStoreRemoval(item.id)) {
                        DeletionOutcome.DELETED
                    } else {
                        DeletionOutcome.FAILED
                    }
                }
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

    /**
     * Deletes [item] through the SAF tree grant, with two strategies:
     *
     * 1. **Direct document id** built from the MediaStore file path — O(1),
     *    and works for files in any subfolder of the grant.
     * 2. **A bounded walk of the granted tree**, matching children by display
     *    name (and size when known). Matching happens **in code, never in the
     *    query**: child-document queries ignore SQL selections on Android's
     *    documents provider, which is exactly how the old implementation
     *    deleted the first file in the folder and still reported success.
     */
    private fun deleteViaSaf(item: ScreenshotMedia, treeUri: Uri): Boolean {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri) ?: return false

        // Strategy 1: direct document id from the file path.
        val directId = safDocumentIdFor(treeDocId, item.path)
        if (directId != null) {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, directId)
            val deleted = runCatching {
                DocumentsContract.deleteDocument(resolver, docUri)
            }.getOrDefault(false)
            if (deleted) return true
        }

        // Strategy 2: walk the tree.
        return findAndDeleteInTree(treeUri, treeDocId, item)
    }

    /** Breadth-first walk of the granted folder tree, matching by name+size. */
    private fun findAndDeleteInTree(treeUri: Uri, rootDocId: String, item: ScreenshotMedia): Boolean {
        data class Entry(val docId: String, val depth: Int)
        val queue = ArrayDeque<Entry>()
        queue.add(Entry(rootDocId, 0))
        var visitedFolders = 0

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        while (queue.isNotEmpty() && visitedFolders < MAX_FOLDERS_VISITED) {
            val (docId, depth) = queue.removeFirst()
            visitedFolders++
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = runCatching {
                resolver.query(childrenUri, projection, null, null, null)
            }.getOrNull() ?: continue
            cursor.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (c.moveToNext()) {
                    val childId = if (idIdx >= 0) c.getString(idIdx) else continue
                    val name = if (nameIdx >= 0) c.getString(nameIdx) else continue
                    val isDir = mimeIdx >= 0 &&
                        c.getString(mimeIdx) == DocumentsContract.Document.MIME_TYPE_DIR
                    if (isDir) {
                        if (depth < MAX_TREE_DEPTH) queue.add(Entry(childId, depth + 1))
                        continue
                    }
                    if (name != item.displayName) continue
                    // Prefer an exact size match so duplicate names (or a same-named
                    // file in another subfolder) cannot win by accident.
                    if (sizeIdx >= 0 && item.sizeBytes > 0) {
                        val size = c.getLong(sizeIdx)
                        if (size > 0 && size != item.sizeBytes) continue
                    }
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val deleted = runCatching {
                        DocumentsContract.deleteDocument(resolver, docUri)
                    }.getOrDefault(false)
                    if (deleted) return true
                }
            }
        }
        return false
    }

    /**
     * After a SAF deletion, waits (briefly) for MediaStore to drop the row.
     * Returns true once the row is gone; false if it is stubbornly present —
     * in which case the caller reports an honest failure.
     */
    private suspend fun waitForMediaStoreRemoval(screenshotId: Long): Boolean {
        repeat(MEDIASTORE_REMOVAL_POLLS) {
            if (findScreenshot(screenshotId) == null) return true
            delay(MEDIASTORE_REMOVAL_POLL_MS)
        }
        return findScreenshot(screenshotId) == null
    }

    companion object {
        /** Folders the SAF tree walk will visit, at most. */
        private const val MAX_FOLDERS_VISITED = 200

        /** Subfolder depth the SAF tree walk will descend, at most. */
        private const val MAX_TREE_DEPTH = 8

        /** How often/how long to poll MediaStore after a SAF delete. */
        private const val MEDIASTORE_REMOVAL_POLLS = 4
        private const val MEDIASTORE_REMOVAL_POLL_MS = 400L

        /**
         * Maps a MediaStore file path to a SAF document id under [treeDocId],
         * when the file provably lives inside the granted folder on primary
         * storage. Pure String logic — unit tested.
         *
         * Example: treeDocId `0:Pictures`, file
         * `/storage/emulated/0/Pictures/Screenshots/S_1.jpg`
         * → `0:Pictures/Screenshots/S_1.jpg`.
         *
         * Returns null whenever the mapping cannot be proven (file outside
         * the grant, removable storage, malformed ids) — callers then fall
         * back to the tree walk.
         */
        fun safDocumentIdFor(treeDocId: String, filePath: String): String? {
            val sep = treeDocId.indexOf(':')
            if (sep <= 0) return null
            val root = treeDocId.substring(0, sep)      // e.g. "0" or "11"
            val folder = treeDocId.substring(sep + 1)    // e.g. "Pictures" ("" = whole storage)
            val primary = "/storage/emulated/0/"
            if (folder.isBlank()) {
                if (!filePath.startsWith(primary)) return null
                val rest = filePath.removePrefix(primary).trimStart('/')
                if (rest.isEmpty() || rest.endsWith("/")) return null
                return "$root:$rest"
            }
            val prefix = "$primary$folder/"
            if (!filePath.startsWith(prefix)) return null
            val rest = filePath.removePrefix(prefix)
            if (rest.isEmpty() || rest.endsWith("/")) return null
            return "$root:$folder/$rest"
        }
    }
}
