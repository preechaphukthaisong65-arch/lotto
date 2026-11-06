package th.co.lotto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import th.co.lotto.data.SaleDocumentEntity
import th.co.lotto.data.SaleLineEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a deterministic receipt image from immutable local sale data.
 * The filename is derived from the immutable document ID so retries replace
 * the receipt image without creating another sale document.
 */
object ReceiptRenderer {
    fun render(
        context: Context,
        document: SaleDocumentEntity,
        periodTitle: String,
        lines: List<SaleLineEntity>
    ): File {
        require(lines.isNotEmpty()) { "Cannot render an empty receipt" }
        val width = 900
        val rowHeight = 62
        val height = 360 + (lines.size * rowHeight) + 120
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF17212B.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        fun line(text: String, x: Float, y: Float, size: Float = 32f, bold: Boolean = false) {
            paint.textSize = size
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(text, x, y, paint)
        }
        line("LOTTO • ใบรับรายการ", 40f, 64f, 42f, true)
        line("งวด: $periodTitle", 40f, 124f)
        line("เลขที่เอกสาร: ${document.documentId}", 40f, 174f, 24f)
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("th", "TH")).format(Date(document.createdAtEpochMillis))
        line("วันที่: $date", 40f, 220f, 26f)
        line("รายการ", 40f, 292f, 30f, true)
        line("ประเภท / เลข", 40f, 340f, 25f, true)
        line("จำนวนเงิน", 680f, 340f, 25f, true)
        var y = 392f
        lines.forEach { item ->
            val category = when (item.betCategory) {
                "TWO_TOP" -> "2 ตัวบน"
                "TWO_BOTTOM" -> "2 ตัวล่าง"
                "THREE_TOP" -> "3 ตัวบน"
                "THREE_BOTTOM" -> "3 ตัวล่าง"
                "THREE_TOAD" -> "3 ตัวโต๊ด"
                else -> item.betCategory
            }
            val half = if (item.halfPayout) " • จ่ายครึ่ง" else ""
            line("$category  ${item.numberText}$half", 40f, y, 26f)
            line(String.format(Locale.US, "%.2f", item.amountSatang / 100.0), 680f, y, 26f)
            y += rowHeight
        }
        val total = document.totalAmountSatang / 100.0
        line("รวมทั้งสิ้น: " + String.format(Locale.US, "%.2f บาท", total), 40f, y + 35f, 34f, true)
        line("เก็บไว้ในอุปกรณ์นี้ • เอกสารต้นฉบับแก้ไขไม่ได้", 40f, y + 92f, 22f)
        val directory = File(context.filesDir, "receipts").apply { mkdirs() }
        val output = File(directory, "${document.documentId}.png")
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return output
    }
}
