package com.example.radiogta

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Findet die Audiodatei zu einem Sender.
 *
 * 1. Vom Nutzer gewählter Ordner (Storage Access Framework)
 * 2. Fallback: OBB-Dateien (alte Installationen)
 */
object RadioFiles {

    private const val TAG = "RadioFiles"
    private const val PREFS_NAME = "radio_prefs"
    private const val KEY_FOLDER_URI = "radio_folder_uri"
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

    // Normalisierter Dateiname -> Uri. null = noch nicht geladen
    @Volatile
    private var folderIndex: Map<String, Uri>? = null

    fun getFolder(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FOLDER_URI, null)?.let(Uri::parse)
    }

    fun setFolder(context: Context, treeUri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FOLDER_URI, treeUri.toString()).apply()
        invalidate()
        Log.d(TAG, "Radio folder set: $treeUri")
    }

    /**
     * Ordnerinhalt beim nächsten Zugriff neu einlesen
     */
    fun invalidate() {
        folderIndex = null
    }

    fun getStationUri(context: Context, station: RadioStation): Uri? {
        getFolderIndex(context)[normalize(station.assetFileName)]?.let { return it }
        return ObbHelper.getRadioFile(context, station.assetFileName)?.let(Uri::fromFile)
    }

    /**
     * Wie getStationUri, aber ohne die Datei aus der OBB zu entpacken
     */
    fun isAvailable(context: Context, station: RadioStation): Boolean {
        return getFolderIndex(context).containsKey(normalize(station.assetFileName)) ||
                ObbHelper.hasRadioFile(context, station.assetFileName)
    }

    private fun getFolderIndex(context: Context): Map<String, Uri> {
        folderIndex?.let { return it }
        val index = getFolder(context)?.let { loadFolderIndex(context, it) } ?: emptyMap()
        folderIndex = index
        return index
    }

    private fun loadFolderIndex(context: Context, treeUri: Uri): Map<String, Uri> {
        val index = mutableMapOf<String, Uri>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0)
                    val displayName = cursor.getString(1) ?: continue
                    if (displayName.substringAfterLast('.', "").lowercase() !in AUDIO_EXTENSIONS) continue

                    index[normalize(displayName)] =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                }
            }
            Log.d(TAG, "Found ${index.size} audio files in radio folder")
        } catch (e: Exception) {
            // z.B. Ordner gelöscht oder Berechtigung entzogen
            Log.e(TAG, "Could not read radio folder: $treeUri", e)
        }
        return index
    }

    /**
     * "Non-Stop-Pop FM.mp3" und "non_stop_pop_fm.mp3" sollen beide passen
     */
    private fun normalize(fileName: String): String {
        return fileName.substringBeforeLast('.').lowercase().filter { it.isLetterOrDigit() }
    }
}
