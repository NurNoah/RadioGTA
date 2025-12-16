package com.example.radiogta

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

object ObbHelper {

    private const val TAG = "ObbHelper"
    private const val OBB_VERSION = 1

    /**
     * Sucht die Datei in Patch OBB -> Main OBB -> Extrahiert sie
     */
    fun getRadioFile(context: Context, assetFileName: String): File? {
        val cacheDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "radio_cache")
        val cachedFile = File(cacheDir, assetFileName)

        // 1. Check Cache
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile
        }

        val internalPath = "radio/$assetFileName"

        // 2. Check Patch OBB (Hat Vorrang vor Main)
        val patchObb = getObbFile(context, "patch")
        if (extractFromZip(patchObb, internalPath, cachedFile)) {
            return cachedFile
        }

        // 3. Check Main OBB
        val mainObb = getObbFile(context, "main")
        if (extractFromZip(mainObb, internalPath, cachedFile)) {
            return cachedFile
        }

        Log.e(TAG, "File not found in any OBB: $assetFileName")
        return null
    }

    private fun getObbFile(context: Context, type: String): File? {
        val obbDir = context.obbDir ?: return null
        // Format: main.1.com.package.obb oder patch.1.com.package.obb
        val fileName = "$type.$OBB_VERSION.${context.packageName}.obb"
        val file = File(obbDir, fileName)
        return if (file.exists()) file else null
    }

    private fun extractFromZip(zipFile: File?, entryName: String, destFile: File): Boolean {
        if (zipFile == null || !zipFile.exists()) return false

        try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return false

                Log.d(TAG, "Extracting $entryName from ${zipFile.name}")

                // Ordner erstellen falls nötig
                destFile.parentFile?.mkdirs()

                zip.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading OBB: ${zipFile.name}", e)
            return false
        }
    }
}