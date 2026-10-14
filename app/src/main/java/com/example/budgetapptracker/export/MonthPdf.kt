package com.example.budgetapptracker.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.budgetapptracker.util.toRupeeString
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// A4 in PDF points (1/72 inch), with a 40-point margin all round.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val LINE = 18f

private val PLUM = Color.rgb(0x5B, 0x2A, 0x86)
private val GREY = Color.rgb(0x6B, 0x6B, 0x78)
private val RULE = Color.rgb(0xDD, 0xDA, 0xE3)
private val dayFormat = DateTimeFormatter.ofPattern("d MMM")

// Draws the month report with Android's built-in PdfDocument: no library, nothing online.
fun writeMonthPdf(report: MonthReport, out: OutputStream) {
    val document = PdfDocument()
    val writer = PageWriter(document)
    val monthName = "${report.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${report.month.year}"

    writer.title("SplitPocket · $monthName")
    writer.note("Generated on ${dayFormat.format(java.time.LocalDate.now())} from the data on this phone")
    writer.gap()

    writer.heading("Summary")
    writer.row(listOf("Income", report.incomePaise.toRupeeString()))
    writer.row(listOf("Allocated to envelopes", report.allocatedPaise.toRupeeString()))
    writer.row(listOf("Spent", report.spentPaise.toRupeeString()))
    writer.row(listOf("Left in envelopes", (report.allocatedPaise - report.spentPaise).toRupeeString()))
    writer.row(listOf("Number of expenses", report.expenses.size.toString()))
    writer.gap()

    if (report.envelopes.isNotEmpty()) {
        writer.heading("Envelopes")
        writer.row(listOf("Envelope", "Budget", "Spent", "Left"), header = true, money = true)
        report.envelopes.forEach {
            writer.row(
                listOf(
                    it.name,
                    it.monthlyBudgetPaise.toRupeeString(),
                    it.spentPaise.toRupeeString(),
                    it.remainingPaise.toRupeeString(),
                ),
                warn = it.remainingPaise < 0,
                money = true,
            )
        }
        writer.gap()
    }

    if (report.topPayees.isNotEmpty()) {
        writer.heading("Where it went")
        report.topPayees.forEach { (name, paise) -> writer.row(listOf(name, paise.toRupeeString())) }
        writer.gap()
    }

    writer.heading("All expenses")
    if (report.expenses.isEmpty()) writer.note("No expenses this month.")
    else {
        writer.row(listOf("Date", "Paid to", "Envelope", "Amount"), header = true)
        val zone = ZoneId.systemDefault()
        report.expenses.forEach {
            writer.row(
                listOf(
                    dayFormat.format(Instant.ofEpochMilli(it.occurredAt).atZone(zone)),
                    it.merchant,
                    it.categoryName ?: "Not sorted",
                    it.amountPaise.toRupeeString(),
                )
            )
        }
    }

    writer.finish()
    document.writeTo(out)
    document.close()
}

// Writes lines from the top of the page down and starts a new page when one fills up.
private class PageWriter(private val document: PdfDocument) {
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var y = 0f

    private val titlePaint = paint(20f, PLUM, bold = true)
    private val headingPaint = paint(13f, PLUM, bold = true)
    private val textPaint = paint(10f, Color.BLACK)
    private val boldPaint = paint(10f, Color.BLACK, bold = true)
    private val warnPaint = paint(10f, Color.rgb(0xB0, 0x24, 0x18))
    private val notePaint = paint(9f, GREY)
    private val rulePaint = Paint().apply { color = RULE; strokeWidth = 0.75f }

    private val canvas: Canvas
        get() = (page ?: newPage()).canvas

    private fun newPage(): PdfDocument.Page {
        page?.let { document.finishPage(it) }
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        return document.startPage(info).also {
            page = it
            y = MARGIN
            it.canvas.drawText("Page $pageNumber", PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 20f, notePaint.right())
        }
    }

    // Moves down by height, turning the page first if that would run into the bottom margin.
    private fun space(height: Float) {
        if (page == null || y + height > PAGE_HEIGHT - MARGIN) newPage()
        y += height
    }

    fun title(text: String) {
        space(28f)
        canvas.drawText(text, MARGIN, y, titlePaint)
    }

    fun heading(text: String) {
        space(LINE + 8f)
        canvas.drawText(text, MARGIN, y, headingPaint)
        y += 4f
    }

    fun note(text: String) {
        space(LINE)
        canvas.drawText(text, MARGIN, y, notePaint)
    }

    fun gap() {
        y += LINE / 2
    }

    // Two cells: a name on the left, an amount on the right.
    // Four cells with money = true: a name, then three amounts lined up on the right.
    // Four cells otherwise: date, payee, envelope, amount.
    fun row(cells: List<String>, header: Boolean = false, warn: Boolean = false, money: Boolean = false) {
        space(LINE)
        val c = canvas
        val width = PAGE_WIDTH - 2 * MARGIN
        val base = if (header) boldPaint else if (warn) warnPaint else textPaint
        if (cells.size == 4 && money) {
            c.drawText(fit(cells[0], width - 330f, base), MARGIN, y, base)
            c.drawText(cells[1], MARGIN + width - 220f, y, base.right())
            c.drawText(cells[2], MARGIN + width - 110f, y, base.right())
            c.drawText(cells[3], MARGIN + width, y, base.right())
        } else if (cells.size == 4) {
            // Date | payee | envelope | amount
            val x = listOf(MARGIN, MARGIN + 60f, MARGIN + 290f)
            c.drawText(fit(cells[0], 55f, base), x[0], y, base)
            c.drawText(fit(cells[1], 225f, base), x[1], y, base)
            c.drawText(fit(cells[2], 120f, base), x[2], y, base)
            c.drawText(cells[3], MARGIN + width, y, base.right())
        } else {
            c.drawText(fit(cells[0], width * 0.6f, base), MARGIN, y, base)
            c.drawText(cells.last(), MARGIN + width, y, base.right())
        }
        c.drawLine(MARGIN, y + 5f, MARGIN + width, y + 5f, rulePaint)
    }

    fun finish() {
        if (page == null) newPage()
        page?.let { document.finishPage(it) }
        page = null
    }

    // Cuts text that would overflow its column and ends it with an ellipsis.
    private fun fit(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun Paint.right() = Paint(this).apply { textAlign = Paint.Align.RIGHT }
}