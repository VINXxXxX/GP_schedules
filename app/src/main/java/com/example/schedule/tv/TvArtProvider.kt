package com.example.schedule.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the TV channel card images to the launcher, read-only:
 *   content://<applicationId>.tvart/art/<name>.png
 *
 * It has to be readable by any launcher (Monet, Android TV home, Projectivy…), so it's
 * exported without a permission. It only ever hands out the PNGs in files/tv_art.
 */
class TvArtProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val segments = uri.pathSegments
        val name = segments.getOrNull(1)
        if (segments.size != 2 || segments[0] != "art" || name == null || !NAME.matches(name)) {
            throw FileNotFoundException(uri.toString())
        }
        val file = File(dir(context ?: throw FileNotFoundException()), name)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private val NAME = Regex("^[a-z0-9_]{1,64}\\.png$")

        fun dir(context: android.content.Context): File =
            File(context.filesDir, "tv_art").apply { mkdirs() }

        fun uri(context: android.content.Context, name: String): Uri =
            Uri.parse("content://${context.packageName}.tvart/art/$name")
    }
}
