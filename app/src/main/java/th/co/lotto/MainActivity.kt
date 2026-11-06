package th.co.lotto

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import th.co.lotto.data.CustomerEntity
import th.co.lotto.data.DrawPeriodEntity
import th.co.lotto.data.LottoDatabase
import th.co.lotto.data.SaleDocumentEntity
import th.co.lotto.data.SaleLineEntity
import th.co.lotto.data.AuditEventEntity
import th.co.lotto.data.PaymentEntryEntity
import th.co.lotto.data.NumberLimitRuleEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.Date
import java.math.BigDecimal
import java.math.RoundingMode
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
    private val scheduleHandler = Handler(Looper.getMainLooper())
    private val scheduleTick = object : Runnable {
        override fun run() {
            advancePeriodSchedule()
            scheduleHandler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    override fun onResume() {
        super.onResume()
        scheduleHandler.removeCallbacks(scheduleTick)
        scheduleHandler.post(scheduleTick)
    }

    override fun onPause() {
        scheduleHandler.removeCallbacks(scheduleTick)
        super.onPause()
    }

    override fun onDestroy() {
        scheduleHandler.removeCallbacks(scheduleTick)
        io.shutdown()
        super.onDestroy()
    }

    private fun advancePeriodSchedule() {
        io.execute {
            try {
                PeriodSchedule.advance(database.lottoDao(), System.currentTimeMillis())
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "ตรวจสอบตารางงวดไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
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
        addCard(currentBody, "หน้าขาย", "บันทึกรายการขายตามงวด") { showSales() }
        addCard(currentBody, "รายการขายและประวัติ", "ค้นหาเอกสาร กรองงวด และดูใบเสร็จย้อนหลัง") { showSalesHistory() }
        addCard(currentBody, "ลูกค้า", "จัดการลูกค้าเงินสดและเงินเชื่อ") { showCustomers() }
        addCard(currentBody, "บัญชีเงินเชื่อ", "ยอดค้างและประวัติรับชำระ") { showCreditLedger() }
        addCard(currentBody, "รับชำระเงิน", "บันทึกรับชำระเต็มจำนวนหรือบางส่วน") { showCreditLedger() }
        addCard(currentBody, "เลขอั้นและเลขเต็ม", "กำหนดวงเงินแยกตามประเภทเลข") { showLimits() }
        addCard(currentBody, "ผลหวยและสรุปยอด", "บันทึกผลและตรวจสอบยอดตามงวด") { showNotReady("ผลหวยและสรุปยอด") }
        addCard(currentBody, "รายงาน", "รายงานยอดขาย ลูกหนี้ และยอดคงเหลือ") { showNotReady("รายงาน") }
        addCard(currentBody, "ตั้งค่าและ PIN", "กำหนดค่าระบบและสิทธิ์การเข้าถึง") { showNotReady("ตั้งค่าและ PIN") }
        addCard(currentBody, "สำรองและกู้คืน", "สำรองข้อมูลลงไฟล์ในเครื่อง") { showNotReady("สำรองและกู้คืน") }
    }

    private fun showPeriods() {
        base("จัดการงวดหวย")
        addButton(currentBody, "＋ สร้าง/เตรียมงวดใหม่") { createPeriodDialog() }
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        io.execute {
            val dao = database.lottoDao()
            PeriodSchedule.advance(dao, System.currentTimeMillis())
            val periods = dao.getPeriods()
            val prepared = dao.getPreparedPeriod()
            runOnUiThread {
                if (periods.isEmpty()) addText(currentBody, "ยังไม่มีข้อมูลงวดหวย", 14, ink)
                periods.forEach { p ->
                    val statusLabel = when (p.status) {
                        "OPEN" -> "เปิดรับขาย"
                        "PREPARED" -> "เตรียมเปิด"
                        "CLOSED" -> "ปิดรับขายแล้ว"
                        else -> p.status
                    }
                    val dateLabel = p.drawDateEpochMillis?.let {
                        SimpleDateFormat("dd/MM/yyyy", Locale("th", "TH")).format(Date(it))
                    } ?: "ยังไม่กำหนดวันหวยออก"
                    val openLabel = p.scheduledOpenEpochMillis?.let {
                        " • กำหนดเปิด " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")).format(Date(it))
                    } ?: ""
                    addCard(currentBody, p.title,
                        "$statusLabel • วันหวยออก $dateLabel$openLabel • รหัสงวด: ${p.periodId.take(8)}") {
                        if (p.status != "CLOSED") editPeriodDateDialog(p)
                    }
                    if (p.status == "OPEN") {
                        addButton(currentBody, "ปิดรับขายงวดนี้") { confirmClosePeriod(p) }
                    }
                }
                if (prepared != null) {
                    addText(currentBody, "งวดถัดไปจะเปิดอัตโนมัติตามเวลาที่เตรียมไว้", 12, 0xFF526171.toInt())
                }
            }
        }
    }

    private fun confirmClosePeriod(period: DrawPeriodEntity) {
        AlertDialog.Builder(this)
            .setTitle("ปิดรับขาย")
            .setMessage("ยืนยันปิดรับขายงวด ${period.title} หรือไม่? เมื่อปิดแล้วจะไม่สามารถบันทึกบิลเพิ่มในงวดนี้ได้")
            .setNegativeButton("กลับ", null)
            .setPositiveButton("ยืนยันปิดงวด") { _, _ ->
                io.execute {
                    try {
                        database.lottoDao().closePeriod(period.periodId, System.currentTimeMillis())
                        runOnUiThread {
                            Toast.makeText(this, "ปิดรับขายงวดแล้ว", Toast.LENGTH_SHORT).show()
                            showPeriods()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { Toast.makeText(this, "ปิดงวดไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            }
            .show()
    }

    private fun editPeriodDateDialog(period: DrawPeriodEntity) {
        val dateInput = EditText(this).apply {
            hint = "วันที่หวยออก (dd/MM/yyyy)"
            period.drawDateEpochMillis?.let {
                setText(SimpleDateFormat("dd/MM/yyyy", Locale("th", "TH")).format(Date(it)))
            }
        }
        AlertDialog.Builder(this)
            .setTitle("กำหนดวันหวยออก • ${period.title}")
            .setView(dateInput)
            .setNegativeButton("กลับ", null)
            .setPositiveButton("บันทึก", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val dateText = dateInput.text.toString().trim()
                        val drawDate = try {
                            SimpleDateFormat("dd/MM/yyyy", Locale("th", "TH"))
                                .apply { isLenient = false }.parse(dateText)?.time
                        } catch (_: Exception) { null }
                        if (drawDate == null) {
                            dateInput.error = "กรุณาระบุวันที่ให้ถูกต้อง"
                            return@setOnClickListener
                        }
                        io.execute {
                            try {
                                val updated = database.lottoDao()
                                    .updateEditablePeriodDrawDate(period.periodId, drawDate)
                                require(updated == 1) { "งวดนี้ไม่อยู่ในสถานะที่แก้ไขวันได้" }
                                runOnUiThread {
                                    Toast.makeText(this, "ปรับวันหวยออกแล้ว", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    showPeriods()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(this, "บันทึกวันหวยออกไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun createPeriodDialog() {
        val titleInput = EditText(this).apply { hint = "ชื่องวด เช่น งวด 16 พ.ย. 2569" }
        val dateInput = EditText(this).apply { hint = "วันที่หวยออก (dd/MM/yyyy) จำเป็น" }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(titleInput)
            addView(dateInput)
            addView(TextView(this@MainActivity).apply {
                text = "ระบบปิดรับขายเวลา 14:00 และเปิดงวดที่เตรียมไว้เวลา 18:00 ตามวันหวยออก"
                textSize = 12f
                setTextColor(0xFF526171.toInt())
            })
        }
        val dialog = AlertDialog.Builder(this).setTitle("สร้าง/เตรียมงวดใหม่").setView(form)
            .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val title = titleInput.text.toString().trim()
                if (title.isBlank()) { titleInput.error = "กรุณาระบุชื่องวด"; return@setOnClickListener }
                val dateText = dateInput.text.toString().trim()
                val drawDate = if (dateText.isBlank()) null else try {
                    SimpleDateFormat("dd/MM/yyyy", Locale("th", "TH")).apply { isLenient = false }.parse(dateText)?.time
                } catch (_: Exception) { null }
                if (drawDate == null) { dateInput.error = "กรุณาระบุวันที่หวยออกให้ถูกต้อง"; return@setOnClickListener }
                saveButton.isEnabled = false
                io.execute {
                    try {
                        val dao = database.lottoDao()
                        val now = System.currentTimeMillis()
                        PeriodSchedule.advance(dao, now)
                        val open = dao.getOpenPeriod()
                        val prepared = dao.getPreparedPeriod()
                        require(prepared == null) { "มีงวดที่เตรียมเปิดอยู่แล้ว: ${prepared?.title}" }

                        val latestClosedDrawDate = if (open == null) {
                            dao.getPeriods().firstOrNull { period ->
                                period.status == "CLOSED" && period.drawDateEpochMillis != null &&
                                    now < PeriodSchedule.openNextAt(period.drawDateEpochMillis)
                            }?.drawDateEpochMillis
                        } else null

                        val scheduleFromDate = open?.drawDateEpochMillis ?: latestClosedDrawDate
                        if (open != null) require(scheduleFromDate != null) {
                            "กรุณากำหนดวันหวยออกของงวดปัจจุบันก่อนเตรียมงวดถัดไป"
                        }
                        val scheduledOpen = scheduleFromDate?.let { PeriodSchedule.openNextAt(it) }
                        val status = if (scheduledOpen != null && scheduledOpen > now) "PREPARED" else "OPEN"
                        dao.insertPeriod(DrawPeriodEntity(
                            UUID.randomUUID().toString(), title, drawDate, status, null, now,
                            if (status == "PREPARED") scheduledOpen else null
                        ))
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                if (status == "PREPARED") "บันทึกงวดถัดไปแล้ว จะเปิดเวลา 18:00" else "เปิดงวดใหม่แล้ว",
                                Toast.LENGTH_LONG
                            ).show()
                            dialog.dismiss()
                            showPeriods()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            saveButton.isEnabled = true
                            Toast.makeText(this, "บันทึกงวดไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
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
                    addCard(currentBody, c.displayName, "${c.customerType} • ${c.phone ?: "ไม่ระบุเบอร์โทร"}") { }
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


    private fun showSales() {
        base("หน้าขาย")
        addText(currentBody, "กรอกรายการบรรทัดละ 1 รายการ: เลข,ประเภท,จำนวนเงิน,HALF หรือ FULL", 13, ink)
        addText(currentBody, "ราคาเท่ากัน: ใส่ยอดเงินเดียวกันทุกบรรทัด • ราคาไม่เท่ากัน: ใส่ยอดแยกแต่ละบรรทัด\nตัวอย่าง: 12,TWO_TOP,20,FULL  •  123,THREE_TOAD,10,HALF", 12, 0xFF526171.toInt())
        addButton(currentBody, "＋ บันทึกบิลขาย") { createSaleDialog() }
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        io.execute {
            val dao = database.lottoDao()
            PeriodSchedule.advance(dao, System.currentTimeMillis())
            val period = dao.getOpenPeriod()
            runOnUiThread {
                addText(currentBody, if (period == null) "ยังไม่มีงวดที่เปิดขาย" else "งวดที่กำลังขาย: ${period.title}", 15, ink)
                if (period == null) addText(currentBody, "กรุณาสร้างงวดก่อนเริ่มขาย", 13, 0xFF9B2C2C.toInt())
            }
        }
    }

    private fun createSaleDialog(correctionOfDocumentId: String? = null) {
        val dao = database.lottoDao()
        io.execute {
            PeriodSchedule.advance(dao, System.currentTimeMillis())
            val period = dao.getOpenPeriod()
            val creditCustomers = dao.getCustomers().filter { it.customerType == "CREDIT" }
            runOnUiThread {
                if (period == null) {
                    Toast.makeText(this, "ยังไม่มีงวดเปิดขาย กรุณาสร้างงวดก่อน", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val correctionSource = correctionOfDocumentId?.let { dao.getSaleDocument(it) }
                if (correctionOfDocumentId != null && (correctionSource == null || correctionSource.periodId != period.periodId || correctionSource.correctionOfDocumentId != null)) {
                    Toast.makeText(this, "เอกสารอ้างอิงต้องเป็นบิลต้นฉบับในงวดที่กำลังเปิดอยู่", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val customerType = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "CREDIT"))
                }
                val customer = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                        listOf("เลือกข้อมูลลูกค้าเงินเชื่อ") + creditCustomers.map { it.displayName })
                }
                val entries = EditText(this).apply {
                    hint = "เลข,ประเภท,จำนวนเงิน,HALF/FULL\\n12,TWO_TOP,20,FULL\\n123,THREE_TOAD,10,HALF"
                    minLines = 5
                    gravity = android.view.Gravity.TOP
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                }
                val totalPreview = TextView(this).apply {
                    text = "ยอดรวม: กรุณากรอกรายการ"
                    textSize = 14f
                    setTextColor(navy)
                    setPadding(0, dp(8), 0, dp(4))
                }
                entries.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        try {
                            val parsed = SaleEntryParser.parse(s?.toString().orEmpty())
                            totalPreview.text = "ยอดรวม " + parsed.size + " รายการ: " + formatMoney(SaleEntryParser.totalSatang(parsed))
                        } catch (e: Exception) {
                            totalPreview.text = "ตรวจสอบรายการ: " + e.message
                        }
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                val form = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(8), dp(20), 0)
                    addView(TextView(this@MainActivity).apply { text = "ประเภทลูกค้า" })
                    addView(customerType)
                    addView(TextView(this@MainActivity).apply { text = "ลูกค้าเงินเชื่อ (ใช้เมื่อเลือก CREDIT)" })
                    addView(customer)
                    addView(TextView(this@MainActivity).apply { text = "รายการขาย" })
                    addView(entries)
                    addView(totalPreview)
                }
                val dialog = AlertDialog.Builder(this).setTitle("บันทึกบิล • ${period.title}").setView(form)
                    .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
                dialog.setOnShowListener {
                    val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    saveButton.setOnClickListener {
                        if (!saveButton.isEnabled) return@setOnClickListener
                        try {
                            val type = customerType.selectedItem.toString()
                            val selectedCustomer = if (type == "CREDIT") {
                                val index = customer.selectedItemPosition - 1
                                require(index >= 0 && index < creditCustomers.size) { "กรุณาเลือกลูกค้าเงินเชื่อ" }
                                creditCustomers[index]
                            } else null
                            val parsed = SaleEntryParser.parse(entries.text.toString())
                            val now = System.currentTimeMillis()
                            val documentId = UUID.randomUUID().toString()
                            val total = SaleEntryParser.totalSatang(parsed)
                            val document = SaleDocumentEntity(
                                documentId, period.periodId, selectedCustomer?.customerId, type,
                                total, now, java.util.TimeZone.getDefault().id, correctionOfDocumentId
                            )
                            val lines = parsed.map { entry ->
                                SaleLineEntity(UUID.randomUUID().toString(), documentId, period.periodId,
                                    entry.number, entry.category, entry.amountSatang, entry.halfPayout)
                            }
                            val audit = AuditEventEntity(UUID.randomUUID().toString(), "SALE_DOCUMENT",
                                documentId, "POSTED", "Sale posted from sales screen", now)
                            saveButton.isEnabled = false
                            io.execute {
                                try {
                                    PeriodSchedule.advance(dao, System.currentTimeMillis())
                                    dao.postSale(document, lines, audit)
                                    try {
                                        val receipt = ReceiptRenderer.render(this, document, period.title, lines)
                                        runOnUiThread {
                                            Toast.makeText(this, "บันทึกบิลสำเร็จ • ใบเสร็จบันทึกในเครื่องแล้ว", Toast.LENGTH_LONG).show()
                                            addText(currentBody, "บิลล่าสุด: ${documentId.take(8)} • ใบเสร็จ: ${receipt.name}", 12, ink)
                                            dialog.dismiss()
                                        }
                                    } catch (receiptError: Exception) {
                                        runOnUiThread {
                                            Toast.makeText(this, "บันทึกบิลแล้ว แต่สร้างภาพใบเสร็จไม่สำเร็จ กรุณาสร้างใหม่จากประวัติบิล: ${receiptError.message}", Toast.LENGTH_LONG).show()
                                            addText(currentBody, "บันทึกบิลแล้ว: ${documentId.take(8)} • ใบเสร็จยังต้องสร้างใหม่จากประวัติ", 12, ink)
                                            dialog.dismiss()
                                        }
                                    }
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        saveButton.isEnabled = true
                                        Toast.makeText(this, "บันทึกไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, e.message ?: "ข้อมูลไม่ถูกต้อง", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                dialog.show()
            }
        }
    }



    private fun showSalesHistory() {
        base("รายการขายและประวัติ")
        val dao = database.lottoDao()
        io.execute {
            val periods = dao.getPeriods()
            runOnUiThread {
                val search = EditText(this).apply { hint = "ค้นหาด้วยเลขที่บิลหรือเลขที่ขาย" }
                val periodPicker = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                        listOf("ทุกงวด") + periods.map { it.title })
                }
                val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                addText(currentBody, "ค้นหาบิลจากเลขที่เอกสารหรือเลขที่อยู่ในรายการ และเลือกกรองตามงวด", 13, ink)
                currentBody.addView(search)
                currentBody.addView(periodPicker)
                addButton(currentBody, "ค้นหา") {
                    val selectedIndex = periodPicker.selectedItemPosition - 1
                    val periodId = if (selectedIndex >= 0) periods[selectedIndex].periodId else null
                    val query = search.text.toString().trim()
                    results.removeAllViews()
                    io.execute {
                        val documents = dao.searchSales(periodId, query)
                        val periodTitles = documents.associate { doc -> doc.periodId to (dao.getPeriod(doc.periodId)?.title ?: "ไม่ทราบชื่องวด") }
                        val halfCounts = documents.associate { doc -> doc.documentId to dao.getLinesForDocument(doc.documentId).count { it.halfPayout } }
                        runOnUiThread {
                            if (documents.isEmpty()) addText(results, "ไม่พบเอกสารขาย", 14, ink)
                            documents.forEach { doc ->
                                val periodTitle = periodTitles[doc.periodId] ?: "ไม่ทราบชื่องวด"
                                val label = if (doc.correctionOfDocumentId == null) "บิลต้นฉบับ" else "เอกสารอ้างอิงบิล ${doc.correctionOfDocumentId.take(8)}"
                                val halfCount = halfCounts[doc.documentId] ?: 0
                                val halfLabel = if (halfCount > 0) " • มีเลขจ่ายครึ่ง $halfCount รายการ" else " • ไม่มีเลขจ่ายครึ่ง"
                                addCard(results, "บิล ${doc.documentId.take(8)} • ${formatMoney(doc.totalAmountSatang)}",
                                    "$periodTitle • $label$halfLabel • ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")).format(Date(doc.createdAtEpochMillis))}") {
                                    showSaleDetail(doc.documentId)
                                }
                            }
                        }
                    }
                }
                currentBody.addView(results)
                addButton(currentBody, "กลับหน้าหลัก") { showHome() }
            }
        }
    }

    private fun showSaleDetail(documentId: String) {
        io.execute {
            val dao = database.lottoDao()
            val document = dao.getSaleDocument(documentId) ?: return@execute
            val period = dao.getPeriod(document.periodId)
            val customer = document.customerId?.let { dao.getCustomer(it) }
            val lines = dao.getLinesForDocument(documentId)
            val receiptFile = java.io.File(java.io.File(filesDir, "receipts"), "${document.documentId}.png")
            runOnUiThread {
                base("รายละเอียดบิล")
                addText(currentBody, "เลขที่เอกสาร: ${document.documentId}", 12, ink)
                addText(currentBody, "งวด: ${period?.title ?: "ไม่ทราบงวด"} • สถานะ: ${period?.status ?: "ไม่ทราบ"}", 14, ink)
                addText(currentBody, "วันที่: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("th", "TH")).format(Date(document.createdAtEpochMillis))}", 14, ink)
                addText(currentBody, "ประเภทลูกค้า: ${document.customerType}${customer?.let { " • ${it.displayName}" } ?: ""}", 14, ink)
                if (document.correctionOfDocumentId != null) addText(currentBody, "อ้างอิงบิลต้นฉบับ: ${document.correctionOfDocumentId}", 12, ink)
                addText(currentBody, "รายการขาย", 17, navy)
                lines.forEach { line ->
                    val category = when (line.betCategory) {
                        "TWO_TOP" -> "2 ตัวบน"
                        "TWO_BOTTOM" -> "2 ตัวล่าง"
                        "THREE_TOP" -> "3 ตัวบน"
                        "THREE_BOTTOM" -> "3 ตัวล่าง"
                        "THREE_TOAD" -> "3 ตัวโต๊ด"
                        else -> line.betCategory
                    }
                    addCard(currentBody, "${line.numberText} • $category",
                        "${formatMoney(line.amountSatang)} • ${if (line.halfPayout) "จ่ายครึ่ง" else "จ่ายเต็ม"}") { }
                }
                addText(currentBody, "ยอดรวม: ${formatMoney(document.totalAmountSatang)}", 18, navy)
                if (receiptFile.exists()) {
                    val receiptBitmap = android.graphics.BitmapFactory.decodeFile(receiptFile.absolutePath)
                    if (receiptBitmap != null) {
                        addText(currentBody, "ภาพใบเสร็จที่บันทึกไว้ในเครื่อง", 14, ink)
                        currentBody.addView(ImageView(this).apply {
                            setImageBitmap(receiptBitmap)
                            adjustViewBounds = true
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        }, LinearLayout.LayoutParams(-1, -2))
                    }
                }
                addButton(currentBody, if (receiptFile.exists()) "สร้างภาพใบเสร็จใหม่จากข้อมูลต้นฉบับ" else "สร้างภาพใบเสร็จจากข้อมูลต้นฉบับ") {
                    io.execute {
                        try {
                            val file = ReceiptRenderer.render(this, document, period?.title ?: "ไม่ทราบงวด", lines)
                            runOnUiThread {
                                Toast.makeText(this, "สร้างใบเสร็จแล้ว: ${file.name}", Toast.LENGTH_LONG).show()
                                showSaleDetail(document.documentId)
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Toast.makeText(this, "สร้างใบเสร็จไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }
                }
                if (period?.status == "OPEN" && document.correctionOfDocumentId == null) {
                    addButton(currentBody, "สร้างเอกสารใหม่อ้างอิงบิลนี้") { createSaleDialog(document.documentId) }
                }
                addButton(currentBody, "กลับประวัติการขาย") { showSalesHistory() }
            }
        }
    }

    private fun showCreditLedger() {
        base("บัญชีเงินเชื่อ")
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        io.execute {
            val dao = database.lottoDao()
            val customers = dao.getCustomers().filter { it.customerType == "CREDIT" }
            val rows = customers.map { customer ->
                val documents = dao.getCreditDocumentsForCustomer(customer.customerId)
                val sales = documents.sumOf { it.totalAmountSatang }
                val paid = dao.getPaymentsTotal(customer.customerId)
                Triple(customer, documents, (sales - paid).coerceAtLeast(0L))
            }
            runOnUiThread {
                if (rows.isEmpty()) addText(currentBody, "ยังไม่มีลูกค้าเงินเชื่อ", 14, ink)
                rows.forEach { (customer, documents, balance) ->
                    addCard(currentBody, customer.displayName,
                        "ยอดขายเชื่อ: ${formatMoney(documents.sumOf { it.totalAmountSatang })} • คงค้าง: ${formatMoney(balance)}") {
                        showCustomerCreditDocuments(customer.customerId, customer.displayName)
                    }
                }
            }
        }
    }

    private fun showCustomerCreditDocuments(customerId: String, customerName: String) {
        base("ยอดค้าง • $customerName")
        addButton(currentBody, "กลับบัญชีเงินเชื่อ") { showCreditLedger() }
        io.execute {
            val dao = database.lottoDao()
            val documents = dao.getCreditDocumentsForCustomer(customerId)
            val rows = documents.map { doc ->
                val paid = dao.getPaymentsTotalForDocument(doc.documentId)
                Triple(doc, paid, (doc.totalAmountSatang - paid).coerceAtLeast(0L))
            }
            runOnUiThread {
                if (rows.isEmpty()) addText(currentBody, "ไม่มีเอกสารเงินเชื่อ", 14, ink)
                rows.forEach { (doc, paid, due) ->
                    addCard(currentBody, "บิล ${doc.documentId.take(8)}",
                        "ยอดบิล: ${formatMoney(doc.totalAmountSatang)} • ชำระแล้ว: ${formatMoney(paid)} • คงเหลือ: ${formatMoney(due)}") {
                        if (due > 0L) createPaymentDialog(doc.documentId, customerId, customerName, due)
                        else Toast.makeText(this, "บิลนี้ชำระครบแล้ว", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun createPaymentDialog(documentId: String, customerId: String, customerName: String, outstandingSatang: Long) {
        val amount = EditText(this).apply {
            hint = "จำนวนเงิน (บาท)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val method = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("CASH", "BANK_TRANSFER", "OTHER"))
        }
        val note = EditText(this).apply { hint = "หมายเหตุ (ไม่บังคับ)" }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply { text = "ลูกค้า: $customerName" })
            addView(TextView(this@MainActivity).apply { text = "บิล: ${documentId.take(8)} • คงเหลือ ${formatMoney(outstandingSatang)}" })
            addView(amount); addView(method); addView(note)
        }
        val dialog = AlertDialog.Builder(this).setTitle("รับชำระเงิน").setView(form)
            .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val satang = BigDecimal(amount.text.toString().trim())
                        .multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
                    require(satang > 0L) { "จำนวนเงินต้องมากกว่า 0" }
                    require(satang <= outstandingSatang) { "จำนวนเงินเกินยอดคงเหลือ" }
                    val now = System.currentTimeMillis()
                    val payment = PaymentEntryEntity(UUID.randomUUID().toString(), documentId, customerId,
                        satang, now, method.selectedItem.toString(), note.text.toString().trim().ifBlank { null })
                    val audit = AuditEventEntity(UUID.randomUUID().toString(), "PAYMENT",
                        payment.paymentId, "POSTED", "Credit payment posted", now)
                    io.execute {
                        try {
                            database.lottoDao().postPayment(payment, audit)
                            runOnUiThread {
                                Toast.makeText(this, "บันทึกรับชำระแล้ว", Toast.LENGTH_SHORT).show()
                                showCustomerCreditDocuments(customerId, customerName)
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Toast.makeText(this, "บันทึกไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this, e.message ?: "ข้อมูลไม่ถูกต้อง", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun formatMoney(satang: Long): String =
        String.format(Locale.US, "%.2f บาท", satang / 100.0)


    private fun showLimits() {
        base("เลขอั้นและเลขเต็ม")
        addButton(currentBody, "จัดการเลขไม่รับ") { showBlockedNumbers() }
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        io.execute {
            val dao = database.lottoDao()
            PeriodSchedule.advance(dao, System.currentTimeMillis())
            val periods = dao.getPeriods().filter { it.status == "OPEN" || it.status == "PREPARED" }
            val rulesByPeriod = periods.associate { it.periodId to dao.getLimitRules(it.periodId) }
            val soldByRule = periods.flatMap { period ->
                rulesByPeriod[period.periodId].orEmpty().map { rule ->
                    rule.ruleId to dao.getSoldAmountForNumber(period.periodId, rule.numberText, rule.betCategory)
                }
            }.toMap()
            runOnUiThread {
                addText(currentBody, "วงเงินแยกตาม 2 ตัวบน/ล่าง และ 3 ตัวบน/ล่าง/โต๊ด ไม่รวมข้ามประเภท", 12, 0xFF526171.toInt())
                if (periods.isEmpty()) addText(currentBody, "ไม่มีงวดเปิดหรือเตรียมไว้", 14, ink)
                periods.forEach { period ->
                    addText(currentBody, "งวด: ${period.title} • ${if (period.status == "OPEN") "เปิดรับขาย" else "เตรียมเปิด"}", 14, navy)
                    addButton(currentBody, "＋ เพิ่มวงเงินในงวดนี้") { createLimitDialog(targetPeriod = period) }
                    val rules = rulesByPeriod[period.periodId].orEmpty()
                    if (rules.isEmpty()) addText(currentBody, "ยังไม่มีการกำหนดเลขอั้น/เลขเต็ม", 12, ink)
                    rules.forEach { rule ->
                        val sold = soldByRule[rule.ruleId] ?: 0L
                        val remaining = (rule.maximumAmountSatang - sold).coerceAtLeast(0L)
                        val status = when {
                            !rule.enabled -> "ปิดใช้งาน"
                            remaining == 0L -> "เต็มแล้ว"
                            else -> "ยังรับได้"
                        }
                        addCard(currentBody, "${rule.numberText} • ${categoryLabel(rule.betCategory)}",
                            "วงเงิน ${formatMoney(rule.maximumAmountSatang)} • ขายแล้ว ${formatMoney(sold)} • คงเหลือ ${formatMoney(remaining)} • $status") {
                            createLimitDialog(rule, period)
                        }
                    }
                }
            }
        }
    }

    private fun categoryLabel(category: String): String = when (category) {
        "TWO_TOP" -> "2 ตัวบน"
        "TWO_BOTTOM" -> "2 ตัวล่าง"
        "THREE_TOP" -> "3 ตัวบน"
        "THREE_BOTTOM" -> "3 ตัวล่าง"
        "THREE_TOAD" -> "3 ตัวโต๊ด"
        else -> category
    }

    private fun showBlockedNumbers() {
        base("จัดการเลขไม่รับ")
        addButton(currentBody, "กลับเลขอั้น/เลขเต็ม") { showLimits() }
        io.execute {
            val dao = database.lottoDao()
            val periods = dao.getPeriods().filter { it.status == "OPEN" || it.status == "PREPARED" }
            val rulesByPeriod = periods.associate { it.periodId to dao.getAllNumberBlockRules(it.periodId) }
            runOnUiThread {
                if (periods.isEmpty()) addText(currentBody, "ไม่มีงวดเปิดหรือเตรียมไว้", 14, ink)
                periods.forEach { period ->
                    addText(currentBody, "งวด: ${period.title} • ${if (period.status == "OPEN") "เปิดรับขาย" else "เตรียมเปิด"}", 14, navy)
                    addButton(currentBody, "＋ เพิ่มเลขไม่รับในงวดนี้") { createBlockedNumberDialog(period) }
                    val rules = rulesByPeriod[period.periodId].orEmpty()
                    if (rules.isEmpty()) addText(currentBody, "ยังไม่มีรายการเลขไม่รับ", 12, ink)
                    rules.forEach { rule ->
                        val typeLabel = when (rule.blockCategory) {
                            "TWO_DIGIT" -> "2 ตัว (บน/ล่าง)"
                            "THREE_TOP" -> "3 ตัวบน"
                            "THREE_BOTTOM" -> "3 ตัวล่าง"
                            "THREE_TOAD" -> "3 ตัวโต๊ด"
                            else -> rule.blockCategory
                        }
                        val status = if (rule.blocked) "ไม่รับ" else "ปลดบล็อกแล้ว"
                        addCard(currentBody, "${rule.numberText} • $typeLabel", status) {
                            if (rule.blocked) {
                                AlertDialog.Builder(this)
                                    .setTitle("ปลดเลขไม่รับ")
                                    .setMessage("ยืนยันเปิดรับเลข ${rule.numberText} ในประเภท $typeLabel หรือไม่?")
                                    .setNegativeButton("กลับ", null)
                                    .setPositiveButton("ยืนยันปลดบล็อก") { _, _ ->
                                        io.execute {
                                            try {
                                                dao.setNumberBlocked(
                                                    rule.ruleId, rule.periodId, rule.blockCategory,
                                                    rule.numberText, false, System.currentTimeMillis(),
                                                    UUID.randomUUID().toString()
                                                )
                                                runOnUiThread {
                                                    Toast.makeText(this, "ปลดเลขไม่รับแล้ว", Toast.LENGTH_SHORT).show()
                                                    showBlockedNumbers()
                                                }
                                            } catch (e: Exception) {
                                                runOnUiThread { Toast.makeText(this, "ทำรายการไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
                                            }
                                        }
                                    }.show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createBlockedNumberDialog(targetPeriod: DrawPeriodEntity) {
        val number = EditText(this).apply {
            hint = "เลข 2 หรือ 3 หลัก"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val labels = listOf("2 ตัว (บน/ล่าง)", "3 ตัวบน", "3 ตัวล่าง", "3 ตัวโต๊ด")
        val categories = listOf("TWO_DIGIT", "THREE_TOP", "THREE_BOTTOM", "THREE_TOAD")
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, labels)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply {
                text = "งวด: ${targetPeriod.title}"
                textSize = 13f
                setTextColor(ink)
            })
            addView(number)
            addView(category)
        }
        val dialog = AlertDialog.Builder(this).setTitle("เพิ่มเลขไม่รับ").setView(form)
            .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val value = number.text.toString().trim()
                val blockCategory = categories[category.selectedItemPosition]
                val requiredLength = if (blockCategory == "TWO_DIGIT") 2 else 3
                if (value.length != requiredLength || !value.all { it in '0'..'9' }) {
                    number.error = "กรุณากรอกเลข $requiredLength หลักให้ถูกต้อง"
                    return@setOnClickListener
                }
                saveButton.isEnabled = false
                io.execute {
                    try {
                        val dao = database.lottoDao()
                        val changed = dao.setNumberBlocked(
                            UUID.randomUUID().toString(), targetPeriod.periodId, blockCategory,
                            value, true, System.currentTimeMillis(), UUID.randomUUID().toString()
                        )
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                if (changed) "เพิ่มเลขไม่รับแล้ว" else "เลขนี้ถูกตั้งเป็นเลขไม่รับอยู่แล้ว",
                                Toast.LENGTH_SHORT
                            ).show()
                            dialog.dismiss()
                            showBlockedNumbers()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            saveButton.isEnabled = true
                            Toast.makeText(this, "บันทึกไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun createLimitDialog(existing: NumberLimitRuleEntity? = null, targetPeriod: DrawPeriodEntity? = null) {
        val categories = listOf("TWO_TOP", "TWO_BOTTOM", "THREE_TOP", "THREE_BOTTOM", "THREE_TOAD")
        val number = EditText(this).apply {
            hint = "เลข 2 หรือ 3 หลัก"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            existing?.let { setText(it.numberText) }
            isEnabled = existing == null
        }
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            existing?.let { setSelection(categories.indexOf(it.betCategory).coerceAtLeast(0)) }
            isEnabled = existing == null
        }
        val maximum = EditText(this).apply {
            hint = "วงเงินสูงสุด (บาท)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            existing?.let { setText(String.format(Locale.US, "%.2f", it.maximumAmountSatang / 100.0)) }
        }
        val enabled = CheckBox(this).apply {
            text = "เปิดใช้กฎวงเงิน"
            isChecked = existing?.enabled ?: true
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(number); addView(category); addView(maximum); addView(enabled)
        }
        val title = if (existing == null) "กำหนดเลขอั้น/เลขเต็ม" else "แก้ไขวงเงิน ${existing.numberText}"
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(form)
            .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val n = number.text.toString().trim()
                    val cat = category.selectedItem.toString()
                    require(n.length in 2..3 && n.all { it in '0'..'9' }) { "กรุณากรอกเลข 2 หรือ 3 หลัก" }
                    require((n.length == 2 && cat.startsWith("TWO_")) || (n.length == 3 && cat.startsWith("THREE_"))) {
                        "ประเภทไม่ตรงกับจำนวนหลัก"
                    }
                    val maxSatang = SaleEntryParser.parseAmountSatang(maximum.text.toString())
                    require(maxSatang > 0L) { "วงเงินต้องมากกว่า 0" }
                    val ruleId = existing?.ruleId ?: UUID.randomUUID().toString()
                    val enabledValue = enabled.isChecked
                    io.execute {
                        try {
                            val dao = database.lottoDao()
                            val periodId = existing?.periodId ?: targetPeriod?.periodId
                                ?: dao.getOpenPeriod()?.periodId ?: dao.getPreparedPeriod()?.periodId
                                ?: error("ไม่มีงวดเปิดหรือเตรียมไว้")
                            val rule = NumberLimitRuleEntity(
                                ruleId, periodId, n.length, cat, n, maxSatang, enabledValue
                            )
                            dao.saveLimitRule(rule)
                            runOnUiThread {
                                Toast.makeText(this, "บันทึกกฎวงเงินแล้ว", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                showLimits()
                            }
                        } catch (e: Exception) {
                            runOnUiThread { Toast.makeText(this, "บันทึกไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, e.message ?: "ข้อมูลไม่ถูกต้อง", Toast.LENGTH_LONG).show()
                }
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
