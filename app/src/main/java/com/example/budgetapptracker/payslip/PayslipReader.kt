package com.example.budgetapptracker.payslip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

sealed interface PayslipRead {
    data class Read(val fields: PayslipFields) : PayslipRead
    // The PDF is locked. Ask for the password and try again.
    data object NeedsPassword : PayslipRead
    data object WrongPassword : PayslipRead
    // Nothing usable: an unreadable file, or a photo too blurry to read.
    data object Unreadable : PayslipRead
}

// Gets the text out of a payslip file and hands it to PayslipParser. Everything runs on the
// phone: PDFs through PdfBox, photos and scanned PDFs through Google's on-device text recognition.
object PayslipReader {

    suspend fun read(context: Context, uri: Uri, password: String? = null): PayslipRead =
        withContext(Dispatchers.IO) {
            val type = context.contentResolver.getType(uri).orEmpty()
            val text = try {
                if (type == "application/pdf") pdfText(context, uri, password) else imageText(context, uri)
            } catch (e: InvalidPasswordException) {
                return@withContext if (password == null) PayslipRead.NeedsPassword else PayslipRead.WrongPassword
            } catch (e: Exception) {
                null
            }
            val fields = text?.let { PayslipParser.parse(it) }
            if (fields == null || !fields.foundAnything) PayslipRead.Unreadable else PayslipRead.Read(fields)
        }

    private suspend fun pdfText(context: Context, uri: Uri, password: String?): String? {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input, password.orEmpty()).use { doc ->
                // sortByPosition keeps each table row on one line: "Gross Pay (A) 71,180 2,64,243".
                PDFTextStripper().apply { sortByPosition = true; endPage = 3 }.getText(doc)
            }
        }
        // A scanned PDF is only pictures, so it has no text. Read its first page like a photo.
        if (text.isNullOrBlank() || text.count { it.isDigit() } < 10) {
            return renderFirstPage(context, uri)?.let { ocr(InputImage.fromBitmap(it, 0)) } ?: text
        }
        return text
    }

    private suspend fun imageText(context: Context, uri: Uri): String? =
        ocr(InputImage.fromFilePath(context, uri))

    // Android's own PDF renderer draws page one as a picture. It cannot open locked PDFs,
    // but locked ones are digital and already have text.
    private fun renderFirstPage(context: Context, uri: Uri): Bitmap? =
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd: ParcelFileDescriptor ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    // About 200 dpi for an A4 page: sharp enough for text recognition.
                    val scale = 2.8f
                    Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                        .apply {
                            eraseColor(Color.WHITE)
                            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                }
            }
        }

    private suspend fun ocr(image: InputImage): String? = suspendCancellableCoroutine { cont ->
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { cont.resume(rows(it)) }
            .addOnFailureListener { cont.resume(null) }
    }

    // Text recognition returns separate boxes for "Gross Pay" and "71,180" when they sit in
    // different table cells. Put boxes that share a row back on one line, left to right,
    // so the parser sees "Gross Pay 71,180" as on paper. A photo is rarely straight, so the
    // boxes are first turned by the page's tilt (the typical slope of the text lines).
    private fun rows(result: Text): String {
        val lines = result.textBlocks.flatMap { it.lines }
        val slopes = lines.mapNotNull { line ->
            val p = line.cornerPoints ?: return@mapNotNull null
            if (p.size < 2 || p[1].x - p[0].x < 40) null
            else kotlin.math.atan2((p[1].y - p[0].y).toDouble(), (p[1].x - p[0].x).toDouble())
        }.sorted()
        val tilt = if (slopes.isEmpty()) 0.0 else slopes[slopes.size / 2]
        val cos = kotlin.math.cos(tilt)
        val sin = kotlin.math.sin(tilt)

        // Each line as (row position, column position, height, text) on the straightened page.
        data class Piece(val y: Double, val x: Double, val height: Double, val text: String)
        val pieces = lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val cx = box.exactCenterX().toDouble()
            val cy = box.exactCenterY().toDouble()
            Piece(y = -cx * sin + cy * cos, x = cx * cos + cy * sin, height = box.height() * cos, text = line.text)
        }
        if (pieces.isEmpty()) return result.text

        // Walk down the page; a line joins the current row when its middle is within half a
        // line height of the row's average middle.
        val rows = mutableListOf<MutableList<Piece>>()
        pieces.sortedBy { it.y }.forEach { piece ->
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(row.map { it.y }.average() - piece.y) <= piece.height / 2) row += piece
            else rows += mutableListOf(piece)
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.x }.joinToString(" ") { it.text } }
    }
}
