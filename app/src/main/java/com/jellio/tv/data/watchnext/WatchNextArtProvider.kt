package com.jellio.tv.data.watchnext

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

// Serves the Continue Watching artwork WatchNextSyncer saved locally to
// the launcher. Read only, and only files in that one folder.
class WatchNextArtProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        val name = uri.lastPathSegment?.takeIf { NAME.matches(it) } ?: throw FileNotFoundException()
        val ctx = context ?: throw FileNotFoundException()
        val file = File(File(ctx.filesDir, DIR), name)
        if (!file.isFile) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val DIR = "watchnext"
        private val NAME = Regex("[A-Za-z0-9-]+\\.jpg")

        fun uriFor(context: Context, file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.watchnextart")
            .appendPath(file.name)
            .build()
    }
}
