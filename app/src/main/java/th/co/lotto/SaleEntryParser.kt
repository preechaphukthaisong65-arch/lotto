package th.co.lotto

import java.math.BigDecimal

data class ParsedSaleEntry(
    val number: String,
    val category: String,
    val amountSatang: Long,
    val halfPayout: Boolean
)

/**
 * Pure input validation shared by the sales UI and local unit tests.
 * Money is stored as integer satang; values with more than two decimals
 * are rejected rather than silently rounded.
 */
object SaleEntryParser {
    private val moneyPattern = Regex("""[0-9]+(?:\.[0-9]{1,2})?""")

    fun parse(text: String): List<ParsedSaleEntry> {
        val entries = text.lineSequence()
            .filter { it.isNotBlank() }
            .map { raw ->
                val parts = raw.split(",").map { it.trim() }
                require(parts.size == 4) { "รูปแบบรายการไม่ถูกต้อง: $raw" }

                val number = parts[0]
                val category = parts[1].uppercase(java.util.Locale.ROOT)
                val amount = parseAmountSatang(parts[2])
                val half = when (parts[3].uppercase(java.util.Locale.ROOT)) {
                    "HALF" -> true
                    "FULL" -> false
                    else -> throw IllegalArgumentException("ระบุ HALF หรือ FULL ในรายการ: $raw")
                }

                require(number.length in 2..3 && number.all { it in '0'..'9' }) {
                    "เลขต้องมี 2 หรือ 3 หลัก: $number"
                }
                require(amount > 0L) { "จำนวนเงินต้องมากกว่า 0" }
                val allowed = if (number.length == 2) {
                    setOf("TWO_TOP", "TWO_BOTTOM")
                } else {
                    setOf("THREE_TOP", "THREE_BOTTOM", "THREE_TOAD")
                }
                require(category in allowed) { "ประเภทไม่ตรงกับจำนวนหลัก: $number" }
                ParsedSaleEntry(number, category, amount, half)
            }.toList()

        require(entries.isNotEmpty()) { "กรุณากรอกรายการขายอย่างน้อย 1 บรรทัด" }
        totalSatang(entries)
        return entries
    }

    fun parseAmountSatang(value: String): Long {
        val normalized = value.trim()
        require(moneyPattern.matches(normalized)) {
            "จำนวนเงินต้องเป็นตัวเลขและมีทศนิยมไม่เกิน 2 ตำแหน่ง"
        }
        return BigDecimal(normalized).movePointRight(2).longValueExact()
    }

    fun totalSatang(entries: Iterable<ParsedSaleEntry>): Long =
        entries.fold(0L) { total, entry -> Math.addExact(total, entry.amountSatang) }
}
