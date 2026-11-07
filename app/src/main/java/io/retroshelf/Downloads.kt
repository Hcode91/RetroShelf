package io.retroshelf

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ShelfDownloads(private val context: Context, private val dao: ShelfDao) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    suspend fun enqueue(title: String, file: GameFile, public: Boolean) = withContext(Dispatchers.IO) {
        UrlSafety.validate(file)
        val filename = "${System.currentTimeMillis()}-${UrlSafety.filename(file.name)}"
        val request = DownloadManager.Request(Uri.parse(file.url)).setTitle(title)
            .setDescription("RetroShelf • ${Uri.parse(file.url).host}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
        if(public) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "RetroShelf/$filename")
        else request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        val id = manager.enqueue(request)
        dao.putDownload(DownloadRecord(id, title, file.url, filename, file.authorization!!, if(public) "Downloads/RetroShelf" else "App storage"))
    }
    suspend fun cancel(record: DownloadRecord) = withContext(Dispatchers.IO) {
        manager.remove(record.id)
        dao.putDownload(record.copy(state = "Cancelled", localUri = null))
    }
    suspend fun retry(record: DownloadRecord) = enqueue(record.title, GameFile(record.filename.substringAfter('-'), record.url, authorization = record.authorization), record.destination != "App storage")
    suspend fun refresh() = withContext(Dispatchers.IO) {
        dao.downloadSnapshot().filter { it.state !in listOf("Cancelled", "Complete") }.forEach { row ->
            manager.query(DownloadManager.Query().setFilterById(row.id))?.use { c ->
                if(c.moveToFirst()) {
                    fun number(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
                    val status = number(DownloadManager.COLUMN_STATUS).toInt()
                    val total = number(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val progress = if(total > 0) (number(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR) * 100 / total).toInt() else 0
                    val state = when(status) {
                        DownloadManager.STATUS_SUCCESSFUL -> "Complete"
                        DownloadManager.STATUS_FAILED -> "Failed (${number(DownloadManager.COLUMN_REASON)}): open source if browser required"
                        DownloadManager.STATUS_PAUSED -> "Paused"
                        DownloadManager.STATUS_RUNNING -> "Downloading"
                        else -> "Queued"
                    }
                    dao.putDownload(row.copy(state = state, progress = if(state == "Complete") 100 else progress,
                        localUri = manager.getUriForDownloadedFile(row.id)?.toString()))
                } else dao.putDownload(row.copy(state = "Removed from system downloads"))
            }
        }
    }
    suspend fun export(record: DownloadRecord, target: Uri) = withContext(Dispatchers.IO) {
        val source = manager.getUriForDownloadedFile(record.id) ?: throw IOException("Downloaded file no longer exists")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Cannot read download" }
            context.contentResolver.openOutputStream(target).use { output ->
                requireNotNull(output) { "Cannot write destination" }; input.copyTo(output)
            }
        }
    }
}
