package th.co.lotto

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import th.co.lotto.data.CustomerEntity
import th.co.lotto.data.DrawPeriodEntity
import th.co.lotto.data.LottoDatabase
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val database by lazy { LottoDatabase.getInstance(applicationContext) }
    private lateinit var root: LinearLayout
    private lateinit var currentBody: LinearLayout
    private val navy = 0xFF17365D.toInt()
    private val blue = 0xFF2463A6.toInt()
    private val pale = 0xFFF3F6FA.toInt()
    private val ink = 0xFF17212B.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun base(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pale)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val header = TextView(this).apply {
            text = title
            textSize = 23f
            setTextColor(navy)
            setPadding(0, dp(8), 0, dp(14))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(header)
        val scroll = ScrollView(this)
        currentBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(currentBody)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showHome() {
        base("Lotto")
        addText(currentBody, "ระบบจัดการข้อมูลแบบออฟไลน์", 16, ink)
        addText(currentBody, "ข้อมูลจัดเก็บในโทรศัพท์ ไม่มี Cloud Database, Backend หรือระบบซิงก์", 13, 0xFF526171.toInt())
        addCard(currentBody, "จัดการงวดหวย", "สร้างงวดใหม่และดูงวดที่บันทึกไว้") { showPeriods() }
        addCard(currentBody, "หน้าขาย", "บันทึกรายการขายตามงวด") { showNotReady("หน้าขาย") }
        addCard(currentBody, "รายการขายและประวัติ", "ค้นหาเอกสารและตรวจสอบรายการต้นฉบับ") { showNotReady("รายการขายและประวัติ") }
        addCard(currentBody, "ลูกค้า", "จัดการลูกค้าเงินสดและเงินเชื่อ") { showCustomers() }
        addCard(currentBody, "บัญชีเงินเชื่อ", "ยอดค้างและประวัติรับชำระ") { showNotReady("บัญชีเงินเชื่อ") }
        addCard(currentBody, "รับชำระเงิน", "บันทึกรับชำระเต็มจำนวนหรือบางส่วน") { showNotReady("รับชำระเงิน") }
        addCard(currentBody, "เลขอั้นและเลขเต็ม", "กำหนดวงเงินแยกตามประเภทเลข") { showNotReady("เลขอั้นและเลขเต็ม") }
        addCard(currentBody, "ผลหวยและสรุปยอด", "บันทึกผลและตรวจสอบยอดตามงวด") { showNotReady("ผลหวยและสรุปยอด") }
        addCard(currentBody, "รายงาน", "รายงานยอดขาย ลูกหนี้ และยอดคงเหลือ") { showNotReady("รายงาน") }
        addCard(currentBody, "ตั้งค่าและ PIN", "กำหนดค่าระบบและสิทธิ์การเข้าถึง") { showNotReady("ตั้งค่าและ PIN") }
        addCard(currentBody, "สำรองและกู้คืน", "สำรองข้อมูลลงไฟล์ในเครื่อง") { showNotReady("สำรองและกู้คืน") }
    }

    private fun showPeriods() {
        base("จัดการงวดหวย")
        addButton(currentBody, "＋ สร้างงวดใหม่") { createPeriodDialog() }
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        io.execute {
            val periods = database.lottoDao().getPeriods()
            runOnUiThread {
                if (periods.isEmpty()) addText(currentBody, "ยังไม่มีข้อมูลงวดหวย", 14, ink)
                periods.forEach { p ->
                    addCard(currentBody, p.title, "สถานะ: \${p.status} • รหัสงวด: \${p.periodId.take(8)}") { }
                }
            }
        }
    }

    private fun createPeriodDialog() {
        val titleInput = EditText(this).apply { hint = "ชื่องวด เช่น งวด 16 พ.ย. 2569" }
        val dateInput = EditText(this).apply { hint = "วันที่หวยออก (dd/MM/yyyy) ไม่บังคับ" }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(titleInput)
            addView(dateInput)
        }
        val dialog = AlertDialog.Builder(this).setTitle("สร้างงวดใหม่").setView(form)
            .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim()
                if (title.isBlank()) { titleInput.error = "กรุณาระบุชื่องวด"; return@setOnClickListener }
                val dateText = dateInput.text.toString().trim()
                val date = if (dateText.isBlank()) null else try {
                    SimpleDateFormat("dd/MM/yyyy", Locale("th", "TH")).apply { isLenient = false }.parse(dateText)?.time
                } catch (_: Exception) { null }
                if (dateText.isNotBlank() && date == null) { dateInput.error = "รูปแบบวันที่ไม่ถูกต้อง"; return@setOnClickListener }
                io.execute {
                    val dao = database.lottoDao()
                    val open = dao.getOpenPeriod()
                    if (open != null) {
                        runOnUiThread { Toast.makeText(this, "ยังมีงวดเปิดอยู่: \${open.title}", Toast.LENGTH_LONG).show() }
                    } else {
                        dao.insertPeriod(DrawPeriodEntity(UUID.randomUUID().toString(), title, date, "OPEN", null, System.currentTimeMillis()))
                        runOnUiThread { Toast.makeText(this, "บันทึกงวดแล้ว", Toast.LENGTH_SHORT).show(); showPeriods() }
                    }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showCustomers() {
        base("ลูกค้า")
        addButton(currentBody, "＋ เพิ่มลูกค้า") { createCustomerDialog() }
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        io.execute {
            val customers = database.lottoDao().getCustomers()
            runOnUiThread {
                if (customers.isEmpty()) addText(currentBody, "ยังไม่มีข้อมูลลูกค้า", 14, ink)
                customers.forEach { c ->
                    addCard(currentBody, c.displayName, "\${c.customerType} • \${c.phone ?: "ไม่ระบุเบอร์โทร"}") { }
                }
            }
        }
    }

    private fun createCustomerDialog() {
        val name = EditText(this).apply { hint = "ชื่อลูกค้า" }
        val phone = EditText(this).apply { hint = "เบอร์โทรศัพท์"; inputType = 3 }
        val note = EditText(this).apply { hint = "ข้อมูลเพิ่มเติมสำหรับระบุตัวลูกค้า" }
        val type = EditText(this).apply { hint = "ประเภท: CASH หรือ CREDIT"; setText("CASH") }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(name); addView(phone); addView(note); addView(type)
        }
        val dialog = AlertDialog.Builder(this).setTitle("เพิ่มลูกค้า").setView(form)
            .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim()
                val p = phone.text.toString().trim()
                val t = type.text.toString().trim().uppercase(Locale.ROOT)
                if (n.isBlank()) { name.error = "กรุณาระบุชื่อลูกค้า"; return@setOnClickListener }
                if (t !in setOf("CASH", "CREDIT")) { type.error = "ระบุ CASH หรือ CREDIT"; return@setOnClickListener }
                if (t == "CREDIT" && (p.isBlank() || note.text.toString().isBlank())) {
                    Toast.makeText(this, "ลูกค้าเงินเชื่อต้องกรอกเบอร์โทรและข้อมูลระบุตัวตน", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                io.execute {
                    database.lottoDao().insertCustomer(CustomerEntity(UUID.randomUUID().toString(), n, p.ifBlank { null }, note.text.toString().trim().ifBlank { null }, t, System.currentTimeMillis()))
                    runOnUiThread { Toast.makeText(this, "บันทึกลูกค้าแล้ว", Toast.LENGTH_SHORT).show(); showCustomers() }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showNotReady(module: String) {
        base(module)
        addText(currentBody, "โมดูลนี้ยังไม่ได้เปิดใช้งาน", 18, ink)
        addText(currentBody, "กำลังพัฒนาตาม SRS 0.2 โดยจะไม่ใช้ Cloud หรือส่งข้อมูลออกจากเครื่อง", 14, 0xFF526171.toInt())
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
    }

    private fun addCard(parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val heading = TextView(this).apply { text = title; textSize = 17f; setTextColor(navy); setTypeface(null, android.graphics.Typeface.BOLD) }
        val sub = TextView(this).apply { text = detail; textSize = 12f; setTextColor(0xFF596777.toInt()); setPadding(0, dp(4), 0, 0) }
        card.addView(heading); card.addView(sub)
        val lp = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(5), 0, dp(5))
        parent.addView(card, lp)
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = android.widget.Button(this).apply { text = label; setTextColor(blue); setOnClickListener { action() } }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addText(parent: LinearLayout, value: String, size: Int, color: Int) {
        parent.addView(TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(6), 0, dp(6)) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
