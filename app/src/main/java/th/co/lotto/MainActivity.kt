package th.co.lotto

import android.app.Activity
import android.content.Intent
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
import th.co.lotto.data.SaleCancellationEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.Date
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val database get() = LottoDatabase.getInstance(applicationContext)
    private val backupRestoreRequest = 702
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
        addCard(currentBody, "ผลหวยและสรุปยอด", "บันทึกผลพร้อมแหล่งที่มาและข้อมูลตรวจสอบ") { showResults() }
        addCard(currentBody, "รายงาน", "รายงานยอดขาย ลูกหนี้ และยอดคงเหลือ") { showReports() }
        addCard(currentBody, "ตั้งค่าอัตราจ่าย", "กำหนดอัตรา 3 ตัวบนและ 3 ตัวโต๊ด") { showSettings() }
        addCard(currentBody, "สำรองและกู้คืน", "ไฟล์สำรองเข้ารหัสด้วยรหัสผ่าน • ย้ายเครื่องและกู้คืนได้") { showBackup() }
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
        val search = EditText(this).apply {
            hint = "ค้นหาชื่อ เบอร์โทร หรือข้อมูลระบุตัวตน"
            setSingleLine(true)
        }
        currentBody.addView(search)
        io.execute {
            val customers = database.lottoDao().getCustomers()
            runOnUiThread {
                val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                currentBody.addView(results)
                fun render(query: String) {
                    results.removeAllViews()
                    val normalized = query.trim()
                    val filtered = customers.filter { customer ->
                        normalized.isBlank() ||
                            customer.displayName.contains(normalized, ignoreCase = true) ||
                            customer.phone.orEmpty().contains(normalized, ignoreCase = true) ||
                            customer.identityNote.orEmpty().contains(normalized, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        addText(results, if (customers.isEmpty()) "ยังไม่มีข้อมูลลูกค้า" else "ไม่พบลูกค้าที่ค้นหา", 14, ink)
                    }
                    filtered.forEach { customer ->
                        val typeLabel = if (customer.customerType == "CREDIT") "เงินเชื่อ" else "เงินสด"
                        addCard(results, customer.displayName,
                            "$typeLabel • ${customer.phone ?: "ไม่ระบุเบอร์โทร"}") { }
                    }
                }
                render("")
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        render(s?.toString().orEmpty())
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
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
                val now = System.currentTimeMillis()
                val customer = CustomerEntity(
                    UUID.randomUUID().toString(), n, p.ifBlank { null },
                    note.text.toString().trim().ifBlank { null }, t, now
                )
                val audit = AuditEventEntity(
                    UUID.randomUUID().toString(), "CUSTOMER", customer.customerId,
                    "CREATED", "Customer record created; type=$t", now
                )
                io.execute {
                    try {
                        database.lottoDao().postCustomer(customer, audit)
                        runOnUiThread {
                            Toast.makeText(this, "บันทึกลูกค้าแล้ว", Toast.LENGTH_SHORT).show()
                            showCustomers()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, "บันทึกลูกค้าไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }


    private fun showSales() {
        base("หน้าขาย")
        addText(currentBody, "เพิ่มรายการเลข ประเภท และจำนวนเงิน ระบบจะตรวจสอบกฎหวยอั้นและกำหนดสถานะจ่ายครึ่งให้อัตโนมัติ", 13, ink)
        addText(currentBody, "ราคาเท่ากัน: ใส่ยอดเงินเท่ากันทุกบรรทัด • ราคาไม่เท่ากัน: ใส่ยอดแยกแต่ละบรรทัด", 12, 0xFF526171.toInt())
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

    private fun createSaleDialog() {
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
                val categories = listOf(
                    "3 ตัวบน" to "THREE_TOP",
                    "3 ตัวโต๊ด" to "THREE_TOAD"
                )
                val customerType = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("เงินสด", "เงินเชื่อ"))
                }
                val customer = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                        listOf("เลือกลูกค้าเงินเชื่อ") + creditCustomers.map { it.displayName })
                }
                val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val totalPreview = TextView(this).apply {
                    text = "ยอดรวม 0 รายการ: 0.00 บาท"
                    textSize = 16f
                    setTextColor(navy)
                    setPadding(0, dp(12), 0, dp(8))
                }

                fun buildEntriesText(): String = (0 until rows.childCount).mapNotNull { index ->
                    val row = rows.getChildAt(index) as? LinearLayout ?: return@mapNotNull null
                    val number = row.getChildAt(0) as EditText
                    val category = row.getChildAt(1) as Spinner
                    val amount = row.getChildAt(2) as EditText
                    val numberText = number.text.toString().trim()
                    val amountText = amount.text.toString().trim()
                    if (numberText.isBlank() && amountText.isBlank()) return@mapNotNull null
                    "$numberText,${categories[category.selectedItemPosition].second},$amountText"
                }.joinToString("\n")

                fun refreshTotal() {
                    try {
                        val parsed = SaleEntryParser.parse(buildEntriesText())
                        totalPreview.text = "ยอดรวม ${parsed.size} รายการ: ${formatMoney(SaleEntryParser.totalSatang(parsed))}"
                    } catch (e: Exception) {
                        totalPreview.text = "กรอกรายการให้ครบ • ${e.message ?: "ข้อมูลยังไม่สมบูรณ์"}"
                    }
                }

                fun addEntryRow() {
                    val number = EditText(this).apply {
                        hint = "เลข"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        setSingleLine(true)
                    }
                    val category = Spinner(this).apply {
                        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories.map { it.first })
                    }
                    val amount = EditText(this).apply {
                        hint = "บาท"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setSingleLine(true)
                    }
                    val remove = TextView(this).apply {
                        text = "ลบ"
                        setTextColor(0xFFB3261E.toInt())
                        setPadding(dp(8), dp(10), dp(8), dp(10))
                        setOnClickListener {
                            if (rows.childCount > 1) rows.removeView(parent as android.view.View)
                            refreshTotal()
                        }
                    }
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        addView(number, LinearLayout.LayoutParams(0, dp(52), 1.0f))
                        addView(category, LinearLayout.LayoutParams(0, dp(52), 1.35f))
                        addView(amount, LinearLayout.LayoutParams(0, dp(52), 1.0f))
                        addView(remove, LinearLayout.LayoutParams(-2, dp(52)))
                    }
                    number.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshTotal()
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                    amount.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshTotal()
                        override fun afterTextChanged(s: Editable?) = Unit
                    })
                    category.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = refreshTotal()
                    }
                    rows.addView(row)
                }

                addEntryRow()
                val addRowButton = android.widget.Button(this).apply {
                    text = "＋ เพิ่มรายการ"
                    setOnClickListener { addEntryRow() }
                }
                val formContent = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    addView(TextView(this@MainActivity).apply { text = "ประเภทลูกค้า" })
                    addView(customerType)
                    addView(TextView(this@MainActivity).apply { text = "เลือกลูกค้าเงินเชื่อ (เมื่อเลือกเงินเชื่อ)" })
                    addView(customer)
                    addView(TextView(this@MainActivity).apply { text = "รายการ: เลข • ประเภท • ราคา (สถานะจ่ายครึ่งตรวจจากกฎหวยอั้น)" })
                    addView(rows)
                    addView(addRowButton)
                    addView(totalPreview)
                }
                val scroll = ScrollView(this).apply { addView(formContent) }
                val dialog = AlertDialog.Builder(this).setTitle("บันทึกบิล • ${period.title}").setView(scroll)
                    .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
                dialog.setOnShowListener {
                    val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    saveButton.setOnClickListener {
                        if (!saveButton.isEnabled) return@setOnClickListener
                        try {
                            val type = if (customerType.selectedItemPosition == 0) "CASH" else "CREDIT"
                            val selectedCustomer = if (type == "CREDIT") {
                                val index = customer.selectedItemPosition - 1
                                require(index >= 0 && index < creditCustomers.size) { "กรุณาเลือกลูกค้าเงินเชื่อ" }
                                creditCustomers[index]
                            } else null
                            val parsed = SaleEntryParser.parse(buildEntriesText())
                            val now = System.currentTimeMillis()
                            val documentId = UUID.randomUUID().toString()
                            val total = SaleEntryParser.totalSatang(parsed)
                            val document = SaleDocumentEntity(
                                documentId, period.periodId, selectedCustomer?.customerId, type,
                                total, now, java.util.TimeZone.getDefault().id
                            )
                            val lines = parsed.map { entry ->
                                SaleLineEntity(UUID.randomUUID().toString(), documentId, period.periodId,
                                    entry.number, entry.category, entry.amountSatang, false)
                            }
                            val audit = AuditEventEntity(UUID.randomUUID().toString(), "SALE_DOCUMENT",
                                documentId, "POSTED", "Sale posted from sales screen", now)
                            saveButton.isEnabled = false
                            io.execute {
                                try {
                                    PeriodSchedule.advance(dao, System.currentTimeMillis())
                                    dao.postSale(document, lines, audit)
                                    val savedLines = dao.getLinesForDocument(documentId)
                                    try {
                                        val receipt = ReceiptRenderer.render(this, document, period.title, savedLines)
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
                        val cancellations = documents.associate { doc -> doc.documentId to dao.getSaleCancellation(doc.documentId) }
                        runOnUiThread {
                            if (documents.isEmpty()) addText(results, "ไม่พบเอกสารขาย", 14, ink)
                            documents.forEach { doc ->
                                val periodTitle = periodTitles[doc.periodId] ?: "ไม่ทราบชื่องวด"
                                val cancellation = cancellations[doc.documentId]
                                val label = if (cancellation != null) "ยกเลิกแล้ว" else "บิลยืนยันแล้ว"
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
            val cancellation = dao.getSaleCancellation(documentId)
            val canCancel = dao.getDrawResults(document.periodId).isEmpty() &&
                dao.getSettlementSnapshot(document.periodId) == null
            val receiptFile = java.io.File(java.io.File(filesDir, "receipts"), "${document.documentId}.png")
            runOnUiThread {
                base("รายละเอียดบิล")
                addText(currentBody, "เลขที่เอกสาร: ${document.documentId}", 12, ink)
                addText(currentBody, "สถานะบิล: ${if (cancellation == null) "ยืนยันแล้ว" else "ยกเลิกแล้ว"}", 15, if (cancellation == null) navy else 0xFF9B2C2C.toInt())
                if (cancellation != null) {
                    addText(currentBody, "ยกเลิกเมื่อ: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("th", "TH")).format(Date(cancellation.cancelledAtEpochMillis))}", 13, ink)
                    addText(currentBody, "เหตุผลการยกเลิก: ${cancellation.reason}", 13, ink)
                    addText(currentBody, "ยอดบิลเดิมที่ยกเลิก: ${formatMoney(cancellation.originalAmountSatang)}", 13, ink)
                }
                addText(currentBody, "งวด: ${period?.title ?: "ไม่ทราบงวด"} • สถานะงวด: ${period?.status ?: "ไม่ทราบ"}", 14, ink)
                addText(currentBody, "วันที่: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("th", "TH")).format(Date(document.createdAtEpochMillis))}", 14, ink)
                addText(currentBody, "ประเภทลูกค้า: ${document.customerType}${customer?.let { " • ${it.displayName}" } ?: ""}", 14, ink)
                if (document.correctionOfDocumentId != null) addText(currentBody, "อ้างอิงบิลต้นฉบับ: ${document.correctionOfDocumentId}", 12, ink)
                addText(currentBody, "รายการขาย", 17, navy)
                lines.forEach { line ->
                    val category = when (line.betCategory) {
                        "THREE_TOP" -> "3 ตัวบน"
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
                if (cancellation == null && canCancel) {
                    addButton(currentBody, "ยกเลิกบิลนี้") { cancelSaleDialog(document) }
                }
                addButton(currentBody, "กลับประวัติการขาย") { showSalesHistory() }
            }
        }
    }

    private fun cancelSaleDialog(document: SaleDocumentEntity) {
        val reason = EditText(this).apply {
            hint = "ระบุเหตุผลที่ยกเลิกบิล (จำเป็น)"
            minLines = 2
            gravity = android.view.Gravity.TOP
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@MainActivity).apply {
                text = "บิล ${document.documentId.take(8)} • ${formatMoney(document.totalAmountSatang)} บาท"
                textSize = 15f
                setTextColor(navy)
            })
            addView(reason)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("ยืนยันยกเลิกบิล")
            .setMessage("บิลเดิมจะไม่ถูกลบ แต่จะถูกทำเครื่องหมายยกเลิกและไม่นำไปคิดยอดขาย วงเงิน ลูกหนี้ หรือผลรางวัล")
            .setView(form)
            .setNegativeButton("กลับ", null)
            .setPositiveButton("ยืนยันยกเลิก", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val note = reason.text.toString().trim()
                if (note.isBlank()) {
                    reason.error = "กรุณาระบุเหตุผลการยกเลิก"
                    return@setOnClickListener
                }
                val now = System.currentTimeMillis()
                val cancellation = SaleCancellationEntity(
                    UUID.randomUUID().toString(), document.documentId,
                    document.totalAmountSatang, note, now
                )
                val audit = AuditEventEntity(
                    UUID.randomUUID().toString(), "SALE_CANCELLATION",
                    cancellation.cancellationId, "CANCELLED",
                    "Bill ${document.documentId} cancelled; amount=${document.totalAmountSatang}; reason=$note",
                    now
                )
                io.execute {
                    try {
                        database.lottoDao().cancelSaleDocument(cancellation, audit)
                        runOnUiThread {
                            Toast.makeText(this, "ยกเลิกบิลและบันทึกประวัติแล้ว", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                            showSaleDetail(document.documentId)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, "ยกเลิกบิลไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
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
                    val paymentHistory = dao.getPaymentsForDocument(doc.documentId)
                    if (paymentHistory.isNotEmpty()) {
                        addText(currentBody, "ประวัติรับชำระ", 12, navy)
                        paymentHistory.forEach { payment ->
                            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH"))
                                .format(Date(payment.paidAtEpochMillis))
                            addText(currentBody,
                                "$date • ${formatMoney(payment.amountSatang)} • ${payment.method}${payment.note?.let { " • $it" } ?: ""}",
                                12, ink)
                        }
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
                addText(currentBody, "วงเงินแยกตาม 3 ตัวบน และ 3 ตัวโต๊ด ไม่รวมยอดข้ามประเภท", 12, 0xFF526171.toInt())
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
                        val payoutStatus = if (rule.halfPayout) " • จ่ายครึ่งอัตโนมัติ" else " • จ่ายเต็ม"
                        addCard(currentBody, "${rule.numberText} • ${categoryLabel(rule.betCategory)}",
                            "วงเงิน ${formatMoney(rule.maximumAmountSatang)} • ขายแล้ว ${formatMoney(sold)} • คงเหลือ ${formatMoney(remaining)} • $status$payoutStatus") {
                            createLimitDialog(rule, period)
                        }
                    }
                }
            }
        }
    }

    private fun categoryLabel(category: String): String = when (category) {
        "THREE_TOP" -> "3 ตัวบน"
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
                            "THREE_TOP" -> "3 ตัวบน"
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
            hint = "เลข 3 หลัก"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val labels = listOf("3 ตัวบน", "3 ตัวโต๊ด")
        val categories = listOf("THREE_TOP", "THREE_TOAD")
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
                val requiredLength = 3
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
        val categories = listOf("THREE_TOP", "THREE_TOAD")
        val number = EditText(this).apply {
            hint = "เลข 3 หลัก"
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
        val halfPayout = CheckBox(this).apply {
            text = "เลขนี้จ่ายครึ่งอัตโนมัติ"
            isChecked = existing?.halfPayout ?: false
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(number); addView(category); addView(maximum); addView(enabled); addView(halfPayout)
        }
        val title = if (existing == null) "กำหนดเลขอั้น/เลขเต็ม" else "แก้ไขวงเงิน ${existing.numberText}"
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(form)
            .setNegativeButton("กลับ", null).setPositiveButton("บันทึก", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val n = number.text.toString().trim()
                    val cat = category.selectedItem.toString()
                    require(n.length == 3 && n.all { it in '0'..'9' }) { "กรุณากรอกเลข 3 หลัก" }
                    require(cat in setOf("THREE_TOP", "THREE_TOAD")) { "เลือกได้เฉพาะ 3 ตัวบนหรือ 3 ตัวโต๊ด" }
                    val maxSatang = SaleEntryParser.parseAmountSatang(maximum.text.toString())
                    require(maxSatang > 0L) { "วงเงินต้องมากกว่า 0" }
                    val ruleId = existing?.ruleId ?: UUID.randomUUID().toString()
                    val enabledValue = enabled.isChecked
                    val halfPayoutValue = halfPayout.isChecked
                    io.execute {
                        try {
                            val dao = database.lottoDao()
                            val periodId = existing?.periodId ?: targetPeriod?.periodId
                                ?: dao.getOpenPeriod()?.periodId ?: dao.getPreparedPeriod()?.periodId
                                ?: error("ไม่มีงวดเปิดหรือเตรียมไว้")
                            val rule = NumberLimitRuleEntity(
                                ruleId, periodId, n.length, cat, n, maxSatang, enabledValue, halfPayoutValue
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

    private fun showResults() {
        base("ผลหวย")
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        io.execute {
            val dao = database.lottoDao()
            val periods = dao.getPeriods()
            runOnUiThread {
                if (periods.isEmpty()) {
                    addText(currentBody, "ยังไม่มีงวดหวย", 14, ink)
                    return@runOnUiThread
                }
                val picker = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                        periods.map { "${it.title} • ${when (it.status) { "CLOSED" -> "ปิดงวด"; "OPEN" -> "เปิดขาย"; else -> "เตรียมเปิด" }}" })
                }
                currentBody.addView(picker)
                val resultList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                addButton(currentBody, "แสดงผลที่บันทึก") {
                    resultList.removeAllViews()
                    val selected = periods[picker.selectedItemPosition]
                    io.execute {
                        val results = dao.getDrawResults(selected.periodId)
                        val settlementSnapshot = dao.getSettlementSnapshot(selected.periodId)
                        val documents = dao.getSalesForPeriod(selected.periodId)
                        val saleLines = documents.flatMap { dao.getLinesForDocument(it.documentId) }
                        val matches = th.co.lotto.DrawResultMatcher.findMatches(results, saleLines)
                        runOnUiThread {
                            if (settlementSnapshot == null) {
                                addText(resultList, "อัตราจ่ายงวดนี้: ยังไม่ได้บันทึก snapshot", 12, 0xFF9B2C2C.toInt())
                            } else {
                                addText(resultList,
                                    "อัตราจ่ายที่ตรึงไว้ • รุ่น ${settlementSnapshot.payoutConfigVersion} • 3 ตัวบน ${BigDecimal.valueOf(settlementSnapshot.threeTopRateHundredths, 2).stripTrailingZeros().toPlainString()} • 3 ตัวโต๊ด ${BigDecimal.valueOf(settlementSnapshot.threeToadRateHundredths, 2).stripTrailingZeros().toPlainString()}",
                                    12, navy)
                            }
                            if (results.isEmpty()) addText(resultList, "ยังไม่มีผลหวยที่บันทึกในงวดนี้", 13, ink)
                            results.forEach { result ->
                                val verified = if (result.verifiedAtEpochMillis == null) "ยังไม่ยืนยัน" else
                                    "ยืนยันแล้ว " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("th", "TH")).format(Date(result.verifiedAtEpochMillis))
                                addCard(resultList, "${result.resultType}: ${result.winningNumber}",
                                    "แหล่งที่มา: ${result.sourceLabel} • $verified") { }
                            }
                            if (results.isNotEmpty()) {
                                addText(resultList, "สรุปรายการที่ตรงกับผลหวย", 16, navy)
                                if (matches.isEmpty()) addText(resultList, "ไม่พบรายการขายที่ถูกรางวัลจากผลที่บันทึก", 13, ink)
                                val grossSales = documents.fold(0L) { total, document ->
                                    Math.addExact(total, document.totalAmountSatang)
                                }
                                val payoutSummary = settlementSnapshot?.let {
                                    runCatching { PayoutCalculator.calculatePeriod(matches, it, grossSales) }.getOrNull()
                                }
                                matches.forEach { match ->
                                    val totalStake = match.lines.fold(0L) { total, line -> Math.addExact(total, line.amountSatang) }
                                    val matchPayout = payoutSummary?.linePayouts
                                        ?.filter { it.resultId == match.resultId && it.line.betCategory == match.betCategory }
                                        ?.fold(0L) { total, item -> Math.addExact(total, item.payoutSatang) }
                                    addText(resultList,
                                        "${match.resultType} • ${match.winningNumber} • ${categoryLabel(match.betCategory)} • ${match.lines.size} รายการ • ยอดแทง ${formatMoney(totalStake)}" +
                                            (matchPayout?.let { " • ยอดจ่าย ${formatMoney(it)}" } ?: ""),
                                        13, ink)
                                }
                                if (payoutSummary != null) {
                                    addText(resultList, "ยอดจ่ายรางวัลรวม: ${formatMoney(payoutSummary.totalPayoutSatang)}", 16, navy)
                                    addText(resultList, "ยอดขายหลังหักรางวัล: ${formatMoney(payoutSummary.salesAfterPayoutSatang)}", 15, navy)
                                    addText(resultList, "คำนวณจากอัตราจ่าย snapshot และปัดเศษ HALF_UP เป็นสตางค์แยกทีละรายการ", 12, 0xFF526171.toInt())
                                } else {
                                    addText(resultList, "ยังคำนวณยอดจ่ายไม่ได้ • ต้องบันทึกอัตราจ่ายประจำงวดก่อน หรือมีข้อผิดพลาดในการคำนวณ", 12, 0xFF9B2C2C.toInt())
                                }
                            }
                        }
                    }
                }
                addButton(currentBody, "บันทึกอัตราจ่ายประจำงวด") {
                    val selectedPeriod = periods[picker.selectedItemPosition]
                    if (selectedPeriod.status != "CLOSED") {
                        Toast.makeText(this, "ต้องปิดงวดก่อนบันทึกอัตราจ่าย", Toast.LENGTH_LONG).show()
                    } else {
                        io.execute {
                            try {
                                val existing = dao.getSettlementSnapshot(selectedPeriod.periodId)
                                if (existing != null) {
                                    runOnUiThread { Toast.makeText(this, "งวดนี้บันทึกอัตราจ่ายไว้แล้ว • รุ่น ${existing.payoutConfigVersion}", Toast.LENGTH_LONG).show() }
                                } else {
                                    val rates = dao.getPayoutRateSettings() ?: error("กรุณาตั้งค่าอัตราจ่ายก่อน")
                                    require(dao.getDrawResults(selectedPeriod.periodId).isNotEmpty()) { "กรุณาบันทึกผลหวยก่อน" }
                                    val now = System.currentTimeMillis()
                                    val snapshot = th.co.lotto.data.SettlementSnapshotEntity(
                                        UUID.randomUUID().toString(), selectedPeriod.periodId,
                                        rates.twoDigitRateHundredths, rates.threeTopRateHundredths,
                                        rates.threeBottomRateHundredths, rates.threeToadRateHundredths,
                                        rates.configVersion, now
                                    )
                                    val audit = AuditEventEntity(UUID.randomUUID().toString(),
                                        "SETTLEMENT", snapshot.settlementId, "SNAPSHOTTED",
                                        "Payout rates snapshot version ${rates.configVersion}", now)
                                    dao.createSettlementSnapshot(snapshot, audit)
                                    runOnUiThread {
                                        Toast.makeText(this, "บันทึกอัตราจ่ายของงวดนี้แล้ว", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread { Toast.makeText(this, "บันทึกอัตราจ่ายไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                }
                addButton(currentBody, "ตรวจสอบเลขที่ถูกรางวัล") {
                    resultList.removeAllViews()
                    val selectedPeriod = periods[picker.selectedItemPosition]
                    io.execute {
                        val savedResults = dao.getDrawResults(selectedPeriod.periodId)
                        val documents = dao.getSalesForPeriod(selectedPeriod.periodId)
                        val lines = documents.flatMap { dao.getLinesForDocument(it.documentId) }
                        val matches = th.co.lotto.DrawResultMatcher.findMatches(savedResults, lines)
                        runOnUiThread {
                            if (savedResults.isEmpty()) addText(resultList, "ยังไม่มีผลหวยสำหรับตรวจสอบ", 13, ink)
                            else if (matches.isEmpty()) addText(resultList, "ยังไม่พบรายการขายที่ตรงกับผลที่บันทึก", 13, ink)
                            matches.forEach { match ->
                                addText(resultList,
                                    "${match.resultType} • เลข ${match.winningNumber} • ${categoryLabel(match.betCategory)}",
                                    15, navy)
                                match.lines.forEach { line ->
                                    addText(resultList,
                                        "บิล ${line.documentId.take(8)} • ยอดแทง ${formatMoney(line.amountSatang)}${if (line.halfPayout) " • จ่ายครึ่งตามกฎงวด" else ""}",
                                        13, ink)
                                }
                            }
                            addText(resultList,
                                "ผลนี้เป็นการจับคู่รายการขายเท่านั้น ยังไม่ใช่ยอดจ่ายเงินจริง • สูตรจ่ายครึ่งกำหนดแล้ว แต่ยังต้องยืนยันการปัดเศษและบัญชีเงินคืน",
                                12, 0xFF9B2C2C.toInt())
                        }
                    }
                }
                addButton(currentBody, "＋ ดึงผลรางวัลจากเว็บไซต์ GLO") {
                    val selected = periods[picker.selectedItemPosition]
                    if (selected.status != "CLOSED") {
                        Toast.makeText(this, "ต้องปิดงวดก่อนจึงจะดึงผลรางวัลได้", Toast.LENGTH_LONG).show()
                        return@addButton
                    }
                    val drawDate = selected.drawDateEpochMillis
                    if (drawDate == null) {
                        Toast.makeText(this, "กรุณากำหนดวันที่ออกรางวัลของงวดนี้ก่อน", Toast.LENGTH_LONG).show()
                        return@addButton
                    }
                    io.execute {
                        try {
                            require(dao.getDrawResults(selected.periodId).none { it.resultType == "THREE_TOP" }) {
                                "งวดนี้มีผลรางวัล 3 ตัวบนบันทึกไว้แล้ว ไม่สามารถนำเข้าซ้ำ"
                            }
                            val official = GloResultClient().fetchTopResult(drawDate)
                            val now = official.fetchedAtEpochMillis
                            val result = th.co.lotto.data.DrawResultEntity(
                                UUID.randomUUID().toString(),
                                selected.periodId,
                                "THREE_TOP",
                                official.threeTopNumber,
                                "GLO Official API • ${official.sourceUrl} • รางวัลที่ 1 ${official.firstPrizeNumber}",
                                now,
                                now
                            )
                            val audit = AuditEventEntity(
                                UUID.randomUUID().toString(),
                                "DRAW_RESULT",
                                result.resultId,
                                "POSTED",
                                "GLO draw ${official.drawDateIso}; first prize ${official.firstPrizeNumber}; THREE_TOP ${official.threeTopNumber}; fetchedAt=$now",
                                now
                            )
                            dao.postDrawResult(result, audit)
                            runOnUiThread {
                                Toast.makeText(this, "นำเข้าผล GLO แล้ว • 3 ตัวบน ${official.threeTopNumber}", Toast.LENGTH_LONG).show()
                                showResults()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this, "ดึงผลจาก GLO ไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                currentBody.addView(resultList)
                addText(currentBody, "ผลรางวัลนำเข้าจาก GLO โดยตรงเท่านั้น • หาก GLO ไม่พร้อมใช้งาน ระบบจะไม่ใช้ผลจากเว็บอื่นแทน • ผลที่บันทึกแล้วใช้งานออฟไลน์ได้", 12, 0xFF526171.toInt())
            }
        }
    }

    private fun showReports() {
        base("รายงาน")
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        io.execute {
            val dao = database.lottoDao()
            val periods = dao.getPeriods()
            runOnUiThread {
                if (periods.isEmpty()) {
                    addText(currentBody, "ยังไม่มีงวดสำหรับสร้างรายงาน", 14, ink)
                    return@runOnUiThread
                }
                val picker = Spinner(this).apply {
                    adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                        periods.map { it.title })
                }
                addText(currentBody, "เลือกงวดที่ต้องการดูรายงาน", 14, ink)
                currentBody.addView(picker)
                addButton(currentBody, "แสดงรายงาน") {
                    results.removeAllViews()
                    val period = periods[picker.selectedItemPosition]
                    io.execute {
                        try {
                            val documents = dao.getSalesForPeriod(period.periodId)
                            val cancellations = dao.getSaleCancellationsForPeriod(period.periodId)
                            val cancelledOriginalAmount = cancellations.fold(0L) { total, item ->
                                Math.addExact(total, item.originalAmountSatang)
                            }
                            val lines = documents.flatMap { dao.getLinesForDocument(it.documentId) }
                            val payments = documents.associate { it.documentId to dao.getPaymentsTotalForDocument(it.documentId) }
                            val report = PeriodReportEngine.calculate(
                                period.periodId, documents, lines, payments,
                                java.time.ZoneId.of("Asia/Bangkok")
                            )
                            runOnUiThread {
                                addText(results, "งวด: ${period.title}", 18, navy)
                                addText(results, "จำนวนเอกสาร: ${report.documentCount} • รายการขาย: ${report.lineCount}", 14, ink)
                                addText(results, "ยอดขายรวมจากเอกสาร: ${formatMoney(report.grossSalesSatang)}", 17, navy)
                                addText(results, "เงินสด: ${formatMoney(report.cashSalesSatang)}", 14, ink)
                                addText(results, "เงินเชื่อ: ${formatMoney(report.creditSalesSatang)}", 14, ink)
                                addText(results, "รับชำระแล้ว: ${formatMoney(report.paymentsReceivedSatang)}", 14, ink)
                                addText(results, "ยอดค้างเงินเชื่อ: ${formatMoney(report.outstandingCreditSatang)}", 14, ink)
                                addText(results, "บิลที่ยกเลิก: ${cancellations.size} ฉบับ • ยอดเดิมรวม ${formatMoney(cancelledOriginalAmount)}", 14, ink)
                                addText(results, "เลขจ่ายครึ่ง: ${report.halfPayoutLineCount} รายการ • ยอดขาย ${formatMoney(report.halfPayoutAmountSatang)}", 14, ink)
                                addText(results, "ยอดแยกประเภท", 16, navy)
                                report.categoryTotalsSatang.forEach { (category, amount) ->
                                    addText(results, "${categoryLabel(category)}: ${formatMoney(amount)}", 14, ink)
                                }
                                addText(results, "ยอดขายรายวัน", 16, navy)
                                report.dailyGrossSalesSatang.forEach { (date, amount) ->
                                    addText(results, "$date • ${formatMoney(amount)}", 14, ink)
                                }
                                addText(results, "ยอดขายรวมไม่รวมบิลที่ยกเลิก • ยอดบิลยกเลิกแสดงแยกด้านบน", 12, 0xFF526171.toInt())
                                addText(results, "ยังไม่คำนวณยอดจ่ายรางวัลหรือยอดหลังหักรางวัล • รอการยืนยันการปัดเศษและวิธีบันทึกเงินคืนจากบิลเงินสดที่ยกเลิก", 13, 0xFF9B2C2C.toInt())
                                addButton(results, "ส่งออกรายงาน CSV") {
                                    val csv = buildString {
                                        appendLine("section,value")
                                        appendLine("period,\"${period.title.replace("\"", "\"\"")}\"")
                                        appendLine("documents,${report.documentCount}")
                                        appendLine("cancelled_bills,${cancellations.size}")
                                        appendLine("cancelled_original_amount_satang,${cancelledOriginalAmount}")
                                        appendLine("gross_sales_satang,${report.grossSalesSatang}")
                                        appendLine("cash_sales_satang,${report.cashSalesSatang}")
                                        appendLine("credit_sales_satang,${report.creditSalesSatang}")
                                        appendLine("payments_received_satang,${report.paymentsReceivedSatang}")
                                        appendLine("outstanding_credit_satang,${report.outstandingCreditSatang}")
                                        appendLine("line_count,${report.lineCount}")
                                        appendLine("half_payout_line_count,${report.halfPayoutLineCount}")
                                        appendLine("half_payout_amount_satang,${report.halfPayoutAmountSatang}")
                                        report.categoryTotalsSatang.forEach { (category, amount) ->
                                            appendLine("category_${category},${amount}")
                                        }
                                        report.dailyGrossSalesSatang.forEach { (date, amount) ->
                                            appendLine("daily_${date},${amount}")
                                        }
                                    }
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_SUBJECT, "Lotto report • ${period.title}")
                                        putExtra(Intent.EXTRA_TEXT, csv)
                                    }
                                    startActivity(Intent.createChooser(share, "ส่งออกรายงาน"))
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { addText(results, "สร้างรายงานไม่สำเร็จ: ${e.message}", 14, 0xFF9B2C2C.toInt()) }
                        }
                    }
                }
                currentBody.addView(results)
            }
        }
    }


    private fun showSettings() {
        base("ตั้งค่าอัตราจ่าย")
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        addText(currentBody, "อัตราจ่ายเป็นตัวคูณต่อเงินเดิมพัน 1 บาท เก็บเป็นทศนิยม 2 ตำแหน่ง และจะใช้กับงวดใหม่เมื่อมีระบบบันทึกการชำระผล", 13, ink)
        addText(currentBody, "กำหนดอัตราจ่ายแยกเฉพาะ 3 ตัวบน และ 3 ตัวโต๊ด", 13, navy)
        io.execute {
            val current = database.lottoDao().getPayoutRateSettings()
            runOnUiThread {
                fun rateText(value: Long?): String =
                    value?.let { BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString() } ?: ""
                fun rateInput(label: String, value: Long?): EditText {
                    val field = EditText(this).apply {
                        hint = label
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setSingleLine(true)
                        setText(rateText(value))
                    }
                    currentBody.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(ink) })
                    currentBody.addView(field)
                    return field
                }
                val top = rateInput("อัตรา 3 ตัวบน", current?.threeTopRateHundredths)
                val toad = rateInput("อัตรา 3 ตัวโต๊ด", current?.threeToadRateHundredths)
                if (current == null) addText(currentBody, "ยังไม่กำหนดอัตรา • กรอกทุกช่องเพื่อเริ่มตั้งค่า", 12, 0xFF9B2C2C.toInt())
                else addText(currentBody, "เวอร์ชันการตั้งค่า: ${current.configVersion}", 12, 0xFF526171.toInt())
                addButton(currentBody, "บันทึกอัตราจ่าย") {
                    try {
                        fun parseRate(field: EditText): Long {
                            val value = BigDecimal(field.text.toString().trim())
                                .multiply(BigDecimal(100)).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
                            require(value > 0L) { "อัตราจ่ายต้องมากกว่า 0" }
                            return value
                        }
                        val now = System.currentTimeMillis()
                        val proposed = th.co.lotto.data.PayoutRateSettingsEntity(
                            settingsId = 1,
                            // Legacy columns remain populated for schema compatibility; they are not sale categories.
                            twoDigitRateHundredths = current?.twoDigitRateHundredths ?: 7000L,
                            threeTopRateHundredths = parseRate(top),
                            threeBottomRateHundredths = current?.threeBottomRateHundredths ?: 50000L,
                            threeToadRateHundredths = parseRate(toad),
                            configVersion = current?.configVersion ?: 0L,
                            updatedAtEpochMillis = now
                        )
                        val audit = AuditEventEntity(
                            UUID.randomUUID().toString(), "PAYOUT_SETTINGS", "1",
                            "UPDATED", "Payout rates changed", now
                        )
                        io.execute {
                            try {
                                val saved = database.lottoDao().savePayoutRateSettings(proposed, audit)
                                runOnUiThread {
                                    Toast.makeText(this, "บันทึกอัตราจ่ายแล้ว • เวอร์ชัน ${saved.configVersion}", Toast.LENGTH_LONG).show()
                                    showSettings()
                                }
                            } catch (e: Exception) {
                                runOnUiThread { Toast.makeText(this, "บันทึกไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show() }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, e.message ?: "กรุณากรอกอัตราจ่ายให้ถูกต้อง", Toast.LENGTH_LONG).show()
                    }
                }
                addText(currentBody, "การจ่ายครึ่งใช้อัตราจ่าย 50% ของอัตราปกติ โดยใช้ยอดเดิมพันเต็มจำนวน • การคำนวณยอดจ่ายจริงจะเปิดหลังผ่านการตรวจสอบระบบ", 12, 0xFF526171.toInt())
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != backupRestoreRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        promptRestorePassphrase(uri)
    }

    private fun showBackup() {
        base("สำรองและกู้คืนข้อมูล")
        addButton(currentBody, "กลับหน้าหลัก") { showHome() }
        addText(currentBody, "สำรองฐานข้อมูลและรูปใบเสร็จทั้งหมดเป็นไฟล์เดียว เข้ารหัส AES-256-GCM แล้วบันทึกลงพื้นที่จัดเก็บในเครื่อง", 15, ink)
        addText(currentBody, "ใช้รหัสผ่านอย่างน้อย 10 ตัวอักษร ไฟล์สามารถย้ายไปอุปกรณ์อื่นและกู้คืนได้โดยใช้รหัสผ่านเดิม", 13, navy)
        addText(currentBody, "สำคัญ: หากลืมรหัสผ่าน จะไม่สามารถกู้คืนข้อมูลจากไฟล์สำรองได้ โปรดเก็บรหัสผ่านแยกจากโทรศัพท์", 13, 0xFF9B2C2C.toInt())
        addButton(currentBody, "สร้างไฟล์สำรองเข้ารหัส") { promptBackupPassphrase() }
        addButton(currentBody, "กู้คืนจากไฟล์สำรอง") {
            AlertDialog.Builder(this)
                .setTitle("ยืนยันการกู้คืนข้อมูล")
                .setMessage("การกู้คืนจะแทนที่ฐานข้อมูลและใบเสร็จที่อยู่ในเครื่องด้วยข้อมูลจากไฟล์สำรอง ต้องการดำเนินการต่อหรือไม่")
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("เลือกไฟล์สำรอง") { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/octet-stream"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivityForResult(intent, backupRestoreRequest)
                }
                .show()
        }
        addText(currentBody, "การกู้คืนจะแทนที่ฐานข้อมูลและใบเสร็จปัจจุบัน หลังตรวจสอบไฟล์และฐานข้อมูลผ่านแล้วเท่านั้น", 12, 0xFF9B2C2C.toInt())
        addText(currentBody, "ไฟล์สำรองจะถูกสร้างใน Downloads/Lotto (Android 10 ขึ้นไป) ไม่ส่งไฟล์ไปยัง Cloud โดยแอป", 12, 0xFF526171.toInt())
    }

    private fun promptBackupPassphrase() {
        val first = EditText(this).apply {
            hint = "รหัสผ่านอย่างน้อย 10 ตัวอักษร"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val second = EditText(this).apply {
            hint = "ยืนยันรหัสผ่าน"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(first)
            addView(second)
        }
        AlertDialog.Builder(this).setTitle("ตั้งรหัสผ่านไฟล์สำรอง").setView(content)
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("เลือกที่บันทึก", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val a = first.text.toString()
                        val b = second.text.toString()
                        if (a.length < 10 || a != b) {
                            Toast.makeText(this, "รหัสผ่านต้องตรงกันและมีอย่างน้อย 10 ตัวอักษร", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        val passphrase = a.toCharArray()
                        first.text.clear()
                        second.text.clear()
                        dialog.dismiss()
                        saveBackupLocally(passphrase)
                    }
                }
                dialog.show()
            }
    }

    private fun saveBackupLocally(passphrase: CharArray) {
        io.execute {
            var createdUri: android.net.Uri? = null
            try {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val fileName = "lotto-backup-$stamp.lotto"
                val location: String
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Lotto")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("สร้างไฟล์ใน Downloads ไม่สำเร็จ")
                    createdUri = uri
                    contentResolver.openOutputStream(uri, "w")?.use { output ->
                        LocalBackupManager.writeEncryptedBackup(this, database, passphrase, output)
                    } ?: error("ไม่สามารถเขียนไฟล์สำรองได้")
                    val published = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    require(contentResolver.update(uri, published, null, null) > 0) {
                        "บันทึกไฟล์สำรองลง Downloads ไม่สำเร็จ"
                    }
                    location = "Downloads/Lotto/$fileName"
                    createdUri = null
                } else {
                    val directory = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        ?: java.io.File(filesDir, "backups")
                    require(directory.exists() || directory.mkdirs()) { "สร้างโฟลเดอร์สำรองไม่สำเร็จ" }
                    val file = java.io.File(directory, fileName)
                    java.io.FileOutputStream(file).use { output ->
                        LocalBackupManager.writeEncryptedBackup(this, database, passphrase, output)
                    }
                    location = file.absolutePath
                }
                runOnUiThread {
                    Toast.makeText(this, "สำรองข้อมูลเข้ารหัสแล้ว: $location", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                createdUri?.let { contentResolver.delete(it, null, null) }
                runOnUiThread {
                    Toast.makeText(this, "สำรองข้อมูลไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                passphrase.fill(' ')
            }
        }
    }

    private fun promptRestorePassphrase(uri: android.net.Uri) {
        val field = EditText(this).apply {
            hint = "รหัสผ่านไฟล์สำรอง"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("ใส่รหัสผ่านเพื่อกู้คืน").setView(field)
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("ตรวจสอบและกู้คืน", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val passphrase = field.text.toString().toCharArray()
                        field.text.clear()
                        if (passphrase.size < 10) {
                            Toast.makeText(this, "รหัสผ่านไม่ถูกต้อง", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        dialog.dismiss()
                        io.execute {
                            try {
                                contentResolver.openInputStream(uri)?.use { input ->
                                    LocalBackupManager.restoreEncryptedBackup(this, input, passphrase)
                                } ?: error("ไม่สามารถอ่านไฟล์สำรองได้")
                                runOnUiThread {
                                    Toast.makeText(this, "กู้คืนข้อมูลสำเร็จ", Toast.LENGTH_LONG).show()
                                    showHome()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(this, "กู้คืนไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                passphrase.fill(' ')
                            }
                        }
                    }
                }
                dialog.show()
            }
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
