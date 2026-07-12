package com.salahlock.app.ui.reflection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.salahlock.app.data.model.MonthlyReport
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a Monthly Reflection to a local PNG or single-page PDF (Feature 11).
 * Pure android.graphics — nothing leaves the device; the user picks the
 * destination via SAF. Layout follows Style E: AMOLED black, gold accents,
 * large Arabic rank, minimal.
 */
object ReflectionExporter {

    private const val WIDTH = 1080
    private const val HEIGHT = 1620

    private const val BLACK = Color.BLACK
    private val GOLD = Color.parseColor("#D4A84F")
    private val TEXT = Color.parseColor("#F5F5F5")
    private val MUTED = Color.parseColor("#A7A7A7")
    private val EMERALD = Color.parseColor("#18A67A")
    private val DIVIDER = Color.parseColor("#2A3335")

    fun renderPng(context: Context, report: MonthlyReport): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), report)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    fun renderPdf(context: Context, report: MonthlyReport): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, 1).create())
        draw(page.canvas, report)
        doc.finishPage(page)
        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    private fun draw(canvas: Canvas, report: MonthlyReport) {
        canvas.drawColor(BLACK)
        val cx = WIDTH / 2f

        fun paint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        }

        var y = 160f

        // Header
        val monthName = report.stats.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        canvas.drawText("$monthName Reflection", cx, y, paint(MUTED, 44f))
        y += 200f

        // Rank — large Arabic, gold
        canvas.drawText(report.rank.arabicName, cx, y, paint(GOLD, 160f, bold = true))
        y += 80f
        canvas.drawText("${report.rank.transliteration} — ${report.rank.translation}", cx, y, paint(TEXT, 40f))
        y += 120f

        canvas.drawLine(cx - 320f, y, cx + 320f, y, Paint().apply { color = DIVIDER; strokeWidth = 2f })
        y += 110f

        // Prayers
        canvas.drawText(
            "${report.stats.completedPrayers}/${report.stats.expectedPrayers} prayers",
            cx, y, paint(TEXT, 72f, bold = true),
        )
        y += 90f
        report.improvementPercent?.let { delta ->
            val sign = if (delta >= 0) "+" else ""
            canvas.drawText("$sign$delta% vs last month", cx, y, paint(EMERALD, 44f))
            y += 90f
        }
        canvas.drawText(
            "Longest streak: ${report.stats.longestStreakInMonth} days   ·   Fajr: ${report.stats.fajrDays} days",
            cx, y, paint(MUTED, 38f),
        )
        y += 170f

        // Motivation quote — wrapped
        val quotePaint = paint(TEXT, 46f).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
        y = drawWrapped(canvas, "“${report.motivation.quote}”", cx, y, quotePaint, WIDTH - 240f, 64f)
        y += 70f
        canvas.drawText(report.motivation.reference, cx, y, paint(GOLD, 38f))

        // Footer
        canvas.drawText("Niyyah · private reflection", cx, HEIGHT - 90f, paint(MUTED, 30f))
    }

    private fun drawWrapped(
        canvas: Canvas, text: String, cx: Float, startY: Float,
        paint: Paint, maxWidth: Float, lineHeight: Float,
    ): Float {
        var y = startY
        var line = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), cx, y, paint)
                y += lineHeight
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line.toString(), cx, y, paint)
        return y
    }
}
