package com.example.budgetapptracker.payslip

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

// Payslip PDFs and photos copied into the app's private storage, where other apps cannot see them.
object PayslipFiles {
    private const val MAX_BYTES = 10L * 1024 * 1024

    private fun folder(context: Context) = File(context.filesDir, "payslips").apply { mkdirs() }

    private fun file(context: Context, name: String) = File(folder(context), name)

    // Copies the file the user picked. Returns its stored name and type, or null if it could not be read
    // or is larger than 10 MB.
    fun copyIn(context: Context, uri: Uri, id: String): Pair<String, String>? {
        val type = context.contentResolver.getType(uri) ?: return null
        val extension = when {
            type == "application/pdf" -> "pdf"
            type.startsWith("image/") -> type.substringAfter('/')
            else -> return null
        }
        val target = file(context, "$id.$extension")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return null
        if (copied > MAX_BYTES) {
            target.delete()
            return null
        }
        return target.name to type
    }

    // Shows the payslip in a PDF viewer or gallery app. FileProvider gives that app a
    // temporary, read-only link to this one file.
    fun open(context: Context, name: String, type: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file(context, name))
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(view, "Open payslip"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun delete(context: Context, name: String) {
        file(context, name).delete()
    }

    // For backups: every stored file's bytes, by file name.
    fun readAll(context: Context, names: List<String>): Map<String, ByteArray> =
        names.mapNotNull { name -> file(context, name).takeIf { it.exists() }?.let { name to it.readBytes() } }
            .toMap()

    // For restores: the folder ends up holding exactly the backup's files.
    fun replaceAll(context: Context, files: Map<String, ByteArray>) {
        folder(context).listFiles()?.forEach { it.delete() }
        files.forEach { (name, bytes) -> file(context, File(name).name).writeBytes(bytes) }
    }
}
