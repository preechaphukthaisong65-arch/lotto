package com.lotto.app.ui.sale


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lotto.app.domain.model.SaleDraft
import com.lotto.app.domain.model.SaleDraftItem
import com.lotto.app.domain.rules.LottoRules
import com.lotto.app.domain.rules.PayoutMode
import com.lotto.app.domain.rules.PrizeType
import com.lotto.app.domain.rules.FullNumberLimitPolicy
import com.lotto.app.ui.components.buttons.BackButton
import com.lotto.app.ui.components.buttons.ConfirmButton
import com.lotto.app.ui.sale.components.SaleNumberRow
import com.lotto.app.ui.sale.model.ThreeDigitEntry
import com.lotto.app.ui.sale.model.TwoDigitEntry
import kotlinx.coroutines.launch

private data class QuotaAdjustment(val entryId: Long, val prizeType: String, val allowedAmount: Long)
private data class NoAcceptClear(val isThreeDigit: Boolean, val entryId: Long)
private data class HalfPayoutPrompt(val isThreeDigit: Boolean, val entryId: Long, val prizeType: String, val number: String)

@Composable
fun SaleScreen(
    onBack: () -> Unit,
    drawDate: String,
    fullLimitsByPrizeType: Map<String, Long>,
    noAcceptNumbersByPrizeType: Map<String, Set<String>>,
    halfPayoutNumbersByPrizeType: Map<String, Set<String>>,
    onGetNumberStake: suspend (String, String, String) -> Long,
    onSave: suspend (SaleDraft) -> Long
) {
    val threeDigitEntries = remember { mutableStateListOf(ThreeDigitEntry(id = 1L)) }
    val twoDigitEntries = remember { mutableStateListOf(TwoDigitEntry(id = 1L)) }
    var nextThreeId by remember { mutableStateOf(2L) }
    var nextTwoId by remember { mutableStateOf(2L) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var cashSelected by remember { mutableStateOf(true) }
    var creditSelected by remember { mutableStateOf(false) }
    var cashAmount by remember { mutableStateOf("") }
    var creditAmount by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val quotaHints = remember { mutableStateMapOf<String, String>() }
    val quotaRequestVersions = remember { mutableStateMapOf<String, Int>() }
    var pendingQuotaAdjustments by remember { mutableStateOf<List<QuotaAdjustment>>(emptyList()) }
    var pendingAddThree by remember { mutableStateOf(false) }
    var pendingAddTwo by remember { mutableStateOf(false) }
    var pendingNoAcceptClear by remember { mutableStateOf<List<NoAcceptClear>>(emptyList()) }
    val acceptedHalfPayoutKeys = remember(halfPayoutNumbersByPrizeType) { mutableStateMapOf<String, Boolean>() }
    var pendingHalfPayouts by remember { mutableStateOf<List<HalfPayoutPrompt>>(emptyList()) }
    var pendingHalfAddThree by remember { mutableStateOf(false) }
    var pendingHalfAddTwo by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun halfKey(prizeType: String, number: String) = "$prizeType|$number"
    fun isHalfAccepted(prizeType: String, number: String) = acceptedHalfPayoutKeys[halfKey(prizeType, number)] == true

    fun refreshQuotaHint(key: String, number: String, prizeType: String, digitCount: Int) {
        val version = (quotaRequestVersions[key] ?: 0) + 1
        quotaRequestVersions[key] = version
        val limit = fullLimitsByPrizeType[prizeType] ?: 0L
        if (number.length != digitCount || limit <= 0L) {
            quotaHints.remove(key)
            return
        }
        coroutineScope.launch {
            runCatching { onGetNumberStake(drawDate, prizeType, number) }
                .onSuccess { used ->
                    if (quotaRequestVersions[key] == version) {
                        quotaHints[key] = when {
                            used >= limit -> "เลข $number เต็มแล้ว · เหลือโควตา 0 บาท"
                            used.toDouble() / limit.toDouble() >= 0.90 ->
                                "เลข $number ใกล้เต็ม · ซื้อได้อีก ${limit - used} บาท"
                            else -> ""
                        }
                        if (quotaHints[key].isNullOrEmpty()) quotaHints.remove(key)
                    }
                }
        }
    }

    suspend fun findAdjustments(
        entries: List<Triple<Long, String, Map<String, Long>>>,
        digitCount: Int
    ): List<QuotaAdjustment> {
        val adjustments = mutableListOf<QuotaAdjustment>()
        val prizeTypes = entries.flatMap { it.third.keys }.distinct()
        for (number in entries.map { it.second }.filter { it.length == digitCount }.distinct()) {
            for (prizeType in prizeTypes) {
                val limit = fullLimitsByPrizeType[prizeType] ?: 0L
                if (limit <= 0L) continue
                var remaining = (limit - onGetNumberStake(drawDate, prizeType, number)).coerceAtLeast(0L)
                entries.filter { it.second == number }.forEach { entry ->
                    val requestedAmount = entry.third[prizeType] ?: 0L
                    if (requestedAmount > remaining) {
                        adjustments += QuotaAdjustment(entry.first, prizeType, remaining)
                    }
                    remaining = (remaining - requestedAmount.coerceAtMost(remaining)).coerceAtLeast(0L)
                }
            }
        }
        return adjustments
    }

    fun requestAddThree() {
        coroutineScope.launch {
            val entries = threeDigitEntries.map { entry ->
                Triple(entry.id, entry.firstDigit + entry.secondDigit + entry.thirdDigit, mapOf(
                    PrizeType.THREE_DIGIT_STRAIGHT.name to (entry.straightAmount.toLongOrNull() ?: 0L),
                    PrizeType.THREE_DIGIT_TOD.name to (entry.boxAmount.toLongOrNull() ?: 0L)
                ))
            }
            val blockedRows = threeDigitEntries.filter { entry ->
                val number = entry.firstDigit + entry.secondDigit + entry.thirdDigit
                number.length == 3 && (
                    ((entry.straightAmount.toLongOrNull() ?: 0L) > 0L && number in (noAcceptNumbersByPrizeType[PrizeType.THREE_DIGIT_STRAIGHT.name] ?: emptySet())) ||
                    ((entry.boxAmount.toLongOrNull() ?: 0L) > 0L && number in (noAcceptNumbersByPrizeType[PrizeType.THREE_DIGIT_TOD.name] ?: emptySet()))
                )
            }
            if (blockedRows.isNotEmpty()) {
                pendingNoAcceptClear = blockedRows.map { NoAcceptClear(true, it.id) }
                return@launch
            }
            val halfRows = threeDigitEntries.flatMap { entry ->
                val number = entry.firstDigit + entry.secondDigit + entry.thirdDigit
                listOf(PrizeType.THREE_DIGIT_STRAIGHT, PrizeType.THREE_DIGIT_TOD).mapNotNull { type ->
                    val amount = if (type == PrizeType.THREE_DIGIT_STRAIGHT) entry.straightAmount.toLongOrNull() ?: 0L else entry.boxAmount.toLongOrNull() ?: 0L
                    if (number.length == 3 && amount > 0L && number in (halfPayoutNumbersByPrizeType[type.name] ?: emptySet()) && !isHalfAccepted(type.name, number)) HalfPayoutPrompt(true, entry.id, type.name, number) else null
                }
            }
            if (halfRows.isNotEmpty()) { pendingHalfPayouts = halfRows; pendingHalfAddThree = true; return@launch }
            val adjustments = runCatching { findAdjustments(entries, 3) }.getOrElse {
                saveError = it.message ?: "ตรวจสอบโควตาไม่สำเร็จ"
                return@launch
            }
            if (adjustments.isEmpty()) {
                threeDigitEntries.add(ThreeDigitEntry(id = nextThreeId))
                nextThreeId += 1L
            } else {
                pendingQuotaAdjustments = adjustments
                pendingAddThree = true
            }
        }
    }

    fun requestAddTwo() {
        coroutineScope.launch {
            val entries = twoDigitEntries.map { entry ->
                Triple(entry.id, entry.firstDigit + entry.secondDigit, mapOf(
                    PrizeType.TWO_DIGIT_TOP.name to (entry.topAmount.toLongOrNull() ?: 0L),
                    PrizeType.TWO_DIGIT_BOTTOM.name to (entry.bottomAmount.toLongOrNull() ?: 0L)
                ))
            }
            val blockedRows = twoDigitEntries.filter { entry ->
                val number = entry.firstDigit + entry.secondDigit
                number.length == 2 && (
                    ((entry.topAmount.toLongOrNull() ?: 0L) > 0L && number in (noAcceptNumbersByPrizeType[PrizeType.TWO_DIGIT_TOP.name] ?: emptySet())) ||
                    ((entry.bottomAmount.toLongOrNull() ?: 0L) > 0L && number in (noAcceptNumbersByPrizeType[PrizeType.TWO_DIGIT_BOTTOM.name] ?: emptySet()))
                )
            }
            if (blockedRows.isNotEmpty()) {
                pendingNoAcceptClear = blockedRows.map { NoAcceptClear(false, it.id) }
                return@launch
            }
            val halfRows = twoDigitEntries.flatMap { entry ->
                val number = entry.firstDigit + entry.secondDigit
                listOf(PrizeType.TWO_DIGIT_TOP, PrizeType.TWO_DIGIT_BOTTOM).mapNotNull { type ->
                    val amount = if (type == PrizeType.TWO_DIGIT_TOP) entry.topAmount.toLongOrNull() ?: 0L else entry.bottomAmount.toLongOrNull() ?: 0L
                    if (number.length == 2 && amount > 0L && number in (halfPayoutNumbersByPrizeType[type.name] ?: emptySet()) && !isHalfAccepted(type.name, number)) HalfPayoutPrompt(false, entry.id, type.name, number) else null
                }
            }
            if (halfRows.isNotEmpty()) { pendingHalfPayouts = halfRows; pendingHalfAddTwo = true; return@launch }
            val adjustments = runCatching { findAdjustments(entries, 2) }.getOrElse {
                saveError = it.message ?: "ตรวจสอบโควตาไม่สำเร็จ"
                return@launch
            }
            if (adjustments.isEmpty()) {
                twoDigitEntries.add(TwoDigitEntry(id = nextTwoId))
                nextTwoId += 1L
            } else {
                pendingQuotaAdjustments = adjustments
                pendingAddTwo = true
            }
        }
    }

    fun confirmQuotaAdjustments() {
        pendingQuotaAdjustments.forEach { adjustment ->
            when (adjustment.prizeType) {
                PrizeType.THREE_DIGIT_STRAIGHT.name -> {
                    val index = threeDigitEntries.indexOfFirst { it.id == adjustment.entryId }
                    if (index >= 0) threeDigitEntries[index] = threeDigitEntries[index].copy(straightAmount = adjustment.allowedAmount.toString())
                }
                PrizeType.THREE_DIGIT_TOD.name -> {
                    val index = threeDigitEntries.indexOfFirst { it.id == adjustment.entryId }
                    if (index >= 0) threeDigitEntries[index] = threeDigitEntries[index].copy(boxAmount = adjustment.allowedAmount.toString())
                }
                PrizeType.TWO_DIGIT_TOP.name -> {
                    val index = twoDigitEntries.indexOfFirst { it.id == adjustment.entryId }
                    if (index >= 0) twoDigitEntries[index] = twoDigitEntries[index].copy(topAmount = adjustment.allowedAmount.toString())
                }
                PrizeType.TWO_DIGIT_BOTTOM.name -> {
                    val index = twoDigitEntries.indexOfFirst { it.id == adjustment.entryId }
                    if (index >= 0) twoDigitEntries[index] = twoDigitEntries[index].copy(bottomAmount = adjustment.allowedAmount.toString())
                }
            }
        }
        if (pendingAddThree) { threeDigitEntries.add(ThreeDigitEntry(id = nextThreeId)); nextThreeId += 1L }
        if (pendingAddTwo) { twoDigitEntries.add(TwoDigitEntry(id = nextTwoId)); nextTwoId += 1L }
        pendingQuotaAdjustments = emptyList()
        pendingAddThree = false
        pendingAddTwo = false
    }

    val total = threeDigitEntries.sumOf {
        (it.straightAmount.toLongOrNull() ?: 0L) + (it.boxAmount.toLongOrNull() ?: 0L)
    } + twoDigitEntries.sumOf {
        (it.topAmount.toLongOrNull() ?: 0L) + (it.bottomAmount.toLongOrNull() ?: 0L)
    }

    val cashValue = when {
        cashSelected && creditSelected -> cashAmount.toLongOrNull() ?: 0L
        cashSelected -> total
        else -> 0L
    }
    val creditValue = when {
        cashSelected && creditSelected -> creditAmount.toLongOrNull() ?: 0L
        creditSelected -> total
        else -> 0L
    }

    val paymentSelected = cashSelected || creditSelected
    val splitValid = cashValue + creditValue == total
    val customerValid = !creditSelected || (firstName.isNotBlank() && lastName.isNotBlank())

    val activeEntriesValid = threeDigitEntries.all { entry ->
        val straightAmount = entry.straightAmount.toLongOrNull() ?: 0L
        val todAmount = entry.boxAmount.toLongOrNull() ?: 0L
        (straightAmount == 0L && todAmount == 0L) ||
            (LottoRules.isValidThreeDigitNumber(
                entry.firstDigit + entry.secondDigit + entry.thirdDigit
            ) && straightAmount >= 0L && todAmount >= 0L)
    } && twoDigitEntries.all { entry ->
        val topAmount = entry.topAmount.toLongOrNull() ?: 0L
        val bottomAmount = entry.bottomAmount.toLongOrNull() ?: 0L
        (topAmount == 0L && bottomAmount == 0L) ||
            (LottoRules.isValidTwoDigitNumber(entry.firstDigit + entry.secondDigit) &&
                topAmount >= 0L && bottomAmount >= 0L)
    }

    val hasNoAcceptItems = threeDigitEntries.any { entry ->
        val number = entry.firstDigit + entry.secondDigit + entry.thirdDigit
        ((entry.straightAmount.toLongOrNull() ?: 0L) > 0L && number in (noAcceptNumbersByPrizeType[PrizeType.THREE_DIGIT_STRAIGHT.name] ?: emptySet())) ||
            ((entry.boxAmount.toLongOrNull() ?: 0L) > 0L && number in (noAcceptNumbersByPrizeType[PrizeType.THREE_DIGIT_TOD.name] ?: emptySet()))
    } || twoDigitEntries.any { entry ->
        val number = entry.firstDigit + entry.secondDigit
        ((entry.topAmount.toLongOrNull() ?: 0L) > 0L && number in (noAcceptNumbersByPrizeType[PrizeType.TWO_DIGIT_TOP.name] ?: emptySet())) ||
            ((entry.bottomAmount.toLongOrNull() ?: 0L) > 0L && number in (noAcceptNumbersByPrizeType[PrizeType.TWO_DIGIT_BOTTOM.name] ?: emptySet()))
    }
    val hasUnacceptedHalfPayoutItems = threeDigitEntries.any { entry ->
        val n = entry.firstDigit + entry.secondDigit + entry.thirdDigit
        ((entry.straightAmount.toLongOrNull() ?: 0L) > 0L && n in (halfPayoutNumbersByPrizeType[PrizeType.THREE_DIGIT_STRAIGHT.name] ?: emptySet()) && !isHalfAccepted(PrizeType.THREE_DIGIT_STRAIGHT.name, n)) ||
            ((entry.boxAmount.toLongOrNull() ?: 0L) > 0L && n in (halfPayoutNumbersByPrizeType[PrizeType.THREE_DIGIT_TOD.name] ?: emptySet()) && !isHalfAccepted(PrizeType.THREE_DIGIT_TOD.name, n))
    } || twoDigitEntries.any { entry ->
        val n = entry.firstDigit + entry.secondDigit
        ((entry.topAmount.toLongOrNull() ?: 0L) > 0L && n in (halfPayoutNumbersByPrizeType[PrizeType.TWO_DIGIT_TOP.name] ?: emptySet()) && !isHalfAccepted(PrizeType.TWO_DIGIT_TOP.name, n)) ||
            ((entry.bottomAmount.toLongOrNull() ?: 0L) > 0L && n in (halfPayoutNumbersByPrizeType[PrizeType.TWO_DIGIT_BOTTOM.name] ?: emptySet()) && !isHalfAccepted(PrizeType.TWO_DIGIT_BOTTOM.name, n))
    }

    fun collectUnacceptedHalfPayouts(): List<HalfPayoutPrompt> = buildList {
        threeDigitEntries.forEach { entry ->
            val number = entry.firstDigit + entry.secondDigit + entry.thirdDigit
            if ((entry.straightAmount.toLongOrNull() ?: 0L) > 0L &&
                number in (halfPayoutNumbersByPrizeType[PrizeType.THREE_DIGIT_STRAIGHT.name] ?: emptySet()) &&
                !isHalfAccepted(PrizeType.THREE_DIGIT_STRAIGHT.name, number)) {
                add(HalfPayoutPrompt(true, entry.id, PrizeType.THREE_DIGIT_STRAIGHT.name, number))
            }
            if ((entry.boxAmount.toLongOrNull() ?: 0L) > 0L &&
                number in (halfPayoutNumbersByPrizeType[PrizeType.THREE_DIGIT_TOD.name] ?: emptySet()) &&
                !isHalfAccepted(PrizeType.THREE_DIGIT_TOD.name, number)) {
                add(HalfPayoutPrompt(true, entry.id, PrizeType.THREE_DIGIT_TOD.name, number))
            }
        }
        twoDigitEntries.forEach { entry ->
            val number = entry.firstDigit + entry.secondDigit
            if ((entry.topAmount.toLongOrNull() ?: 0L) > 0L &&
                number in (halfPayoutNumbersByPrizeType[PrizeType.TWO_DIGIT_TOP.name] ?: emptySet()) &&
                !isHalfAccepted(PrizeType.TWO_DIGIT_TOP.name, number)) {
                add(HalfPayoutPrompt(false, entry.id, PrizeType.TWO_DIGIT_TOP.name, number))
            }
            if ((entry.bottomAmount.toLongOrNull() ?: 0L) > 0L &&
                number in (halfPayoutNumbersByPrizeType[PrizeType.TWO_DIGIT_BOTTOM.name] ?: emptySet()) &&
                !isHalfAccepted(PrizeType.TWO_DIGIT_BOTTOM.name, number)) {
                add(HalfPayoutPrompt(false, entry.id, PrizeType.TWO_DIGIT_BOTTOM.name, number))
            }
        }
    }

    val canSave = total > 0L && paymentSelected && splitValid &&
        customerValid && activeEntriesValid && !hasNoAcceptItems && !isSaving

    fun createDraft(): SaleDraft {
        val items = buildList {
            threeDigitEntries.forEach { entry ->
                val number = entry.firstDigit + entry.secondDigit + entry.thirdDigit
                val straight = entry.straightAmount.toLongOrNull() ?: 0L
                val tod = entry.boxAmount.toLongOrNull() ?: 0L
                if (straight > 0L) add(
                    SaleDraftItem(
                        number = number,
                        prizeType = PrizeType.THREE_DIGIT_STRAIGHT.name,
                        amount = straight,
                        payoutMultiplier = LottoRules.payoutMultiplier(
                            PrizeType.THREE_DIGIT_STRAIGHT,
                            if (isHalfAccepted(PrizeType.THREE_DIGIT_STRAIGHT.name, number)) PayoutMode.HALF else PayoutMode.FULL
                        )
                    )
                )
                if (tod > 0L) add(
                    SaleDraftItem(
                        number = number,
                        prizeType = PrizeType.THREE_DIGIT_TOD.name,
                        amount = tod,
                        payoutMultiplier = LottoRules.payoutMultiplier(
                            PrizeType.THREE_DIGIT_TOD,
                            if (isHalfAccepted(PrizeType.THREE_DIGIT_TOD.name, number)) PayoutMode.HALF else PayoutMode.FULL
                        )
                    )
                )
            }
            twoDigitEntries.forEach { entry ->
                val number = entry.firstDigit + entry.secondDigit
                val top = entry.topAmount.toLongOrNull() ?: 0L
                val bottom = entry.bottomAmount.toLongOrNull() ?: 0L
                if (top > 0L) add(
                    SaleDraftItem(
                        number = number,
                        prizeType = PrizeType.TWO_DIGIT_TOP.name,
                        amount = top,
                        payoutMultiplier = LottoRules.payoutMultiplier(
                            PrizeType.TWO_DIGIT_TOP,
                            if (isHalfAccepted(PrizeType.TWO_DIGIT_TOP.name, number)) PayoutMode.HALF else PayoutMode.FULL
                        )
                    )
                )
                if (bottom > 0L) add(
                    SaleDraftItem(
                        number = number,
                        prizeType = PrizeType.TWO_DIGIT_BOTTOM.name,
                        amount = bottom,
                        payoutMultiplier = LottoRules.payoutMultiplier(
                            PrizeType.TWO_DIGIT_BOTTOM,
                            if (isHalfAccepted(PrizeType.TWO_DIGIT_BOTTOM.name, number)) PayoutMode.HALF else PayoutMode.FULL
                        )
                    )
                )
            }
        }
        return SaleDraft(
            customerFirstName = firstName.trim(),
            customerLastName = lastName.trim(),
            totalAmount = total,
            cashAmount = cashValue,
            creditAmount = creditValue,
            items = items
        )
    }

    if (pendingNoAcceptClear.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("เลขนี้ไม่รับ") },
            text = {
                Text("เลขที่กรอกถูกตั้งค่าเป็นหวยไม่รับ ระบบจะล้างเลขนี้ให้กรอกเลขใหม่ โดยคงยอดเงินที่กรอกไว้")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingNoAcceptClear.forEach { target ->
                        if (target.isThreeDigit) {
                            val index = threeDigitEntries.indexOfFirst { it.id == target.entryId }
                            if (index >= 0) {
                                threeDigitEntries[index] = threeDigitEntries[index].copy(
                                    firstDigit = "", secondDigit = "", thirdDigit = ""
                                )
                            }
                        } else {
                            val index = twoDigitEntries.indexOfFirst { it.id == target.entryId }
                            if (index >= 0) {
                                twoDigitEntries[index] = twoDigitEntries[index].copy(
                                    firstDigit = "", secondDigit = ""
                                )
                            }
                        }
                    }
                    pendingNoAcceptClear = emptyList()
                }) { Text("รับทราบ") }
            }
        )
    }

    if (pendingHalfPayouts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("เลขนี้จ่ายครึ่งราคา") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pendingHalfPayouts.distinctBy { it.prizeType to it.number }.forEach { prompt ->
                        Text("เลข ${prompt.number} (${FullNumberLimitPolicy.labels[prompt.prizeType] ?: prompt.prizeType}) ถูกกำหนดให้จ่ายรางวัลครึ่งราคา")
                    }
                    Text("หากยอมรับและถูกรางวัล จะได้รับเงินรางวัล 50% ของอัตราปกติ")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingHalfPayouts.forEach { acceptedHalfPayoutKeys[halfKey(it.prizeType, it.number)] = true }
                    val addThree = pendingHalfAddThree
                    val addTwo = pendingHalfAddTwo
                    pendingHalfPayouts = emptyList()
                    pendingHalfAddThree = false
                    pendingHalfAddTwo = false
                    if (addThree) requestAddThree()
                    if (addTwo) requestAddTwo()
                }) { Text("ยอมรับ") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingHalfPayouts.forEach { prompt ->
                        if (prompt.isThreeDigit) {
                            val i = threeDigitEntries.indexOfFirst { it.id == prompt.entryId }
                            if (i >= 0) threeDigitEntries[i] = threeDigitEntries[i].copy(firstDigit = "", secondDigit = "", thirdDigit = "")
                        } else {
                            val i = twoDigitEntries.indexOfFirst { it.id == prompt.entryId }
                            if (i >= 0) twoDigitEntries[i] = twoDigitEntries[i].copy(firstDigit = "", secondDigit = "")
                        }
                    }
                    pendingHalfPayouts = emptyList()
                    pendingHalfAddThree = false
                    pendingHalfAddTwo = false
                }) { Text("เปลี่ยนเลข") }
            }
        )
    }

    if (pendingQuotaAdjustments.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                pendingQuotaAdjustments = emptyList()
                pendingAddThree = false
                pendingAddTwo = false
            },
            title = { Text("ปรับยอดซื้อให้ตรงกับโควตาที่เหลือ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingQuotaAdjustments.forEach { adjustment ->
                        val label = FullNumberLimitPolicy.labels[adjustment.prizeType] ?: adjustment.prizeType
                        val number = if (adjustment.prizeType.startsWith("THREE")) {
                            threeDigitEntries.firstOrNull { it.id == adjustment.entryId }?.let { it.firstDigit + it.secondDigit + it.thirdDigit }.orEmpty()
                        } else {
                            twoDigitEntries.firstOrNull { it.id == adjustment.entryId }?.let { it.firstDigit + it.secondDigit }.orEmpty()
                        }
                        val entered = when (adjustment.prizeType) {
                            PrizeType.THREE_DIGIT_STRAIGHT.name -> threeDigitEntries.firstOrNull { it.id == adjustment.entryId }?.straightAmount
                            PrizeType.THREE_DIGIT_TOD.name -> threeDigitEntries.firstOrNull { it.id == adjustment.entryId }?.boxAmount
                            PrizeType.TWO_DIGIT_TOP.name -> twoDigitEntries.firstOrNull { it.id == adjustment.entryId }?.topAmount
                            else -> twoDigitEntries.firstOrNull { it.id == adjustment.entryId }?.bottomAmount
                        }?.toLongOrNull() ?: 0L
                        Text("เลข $number ($label): กรอก $entered บาท แต่ซื้อได้อีก ${adjustment.allowedAmount} บาท")
                    }
                    Text("หากยืนยัน ระบบจะปรับยอดรายการให้เท่ากับโควตาที่เหลือ แล้วเพิ่มแถวรายการใหม่")
                }
            },
            confirmButton = { TextButton(onClick = { confirmQuotaAdjustments() }) { Text("ยืนยันปรับยอดและเพิ่มรายการ") } },
            dismissButton = {
                TextButton(onClick = {
                    pendingQuotaAdjustments = emptyList()
                    pendingAddThree = false
                    pendingAddTwo = false
                }) { Text("ยกเลิก") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(
                    text = "ขายหวย",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("ยอดรวม", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${total} บาท",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (saveError != null) {
                        Text(
                            text = saveError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BackButton(onBack = onBack)
                        Spacer(Modifier.weight(1f))
                        ConfirmButton(
                        onConfirm = {
                            if (hasUnacceptedHalfPayoutItems) {
                                pendingHalfPayouts = collectUnacceptedHalfPayouts()
                                pendingHalfAddThree = false
                                pendingHalfAddTwo = false
                            } else if (canSave) {
                                coroutineScope.launch {
                                    isSaving = true
                                    saveError = null
                                    try {
                                        onSave(createDraft())
                                    } catch (error: Exception) {
                                        saveError = error.message ?: "บันทึกข้อมูลไม่สำเร็จ"
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        label = if (isSaving) "กำลังบันทึก..." else "ยืนยัน / บันทึก",
                        confirmationTitle = "ยืนยันการบันทึกการขาย",
                        confirmationMessage = "ตรวจสอบรายการและยอดรวมก่อนยืนยัน",
                        enabled = canSave
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ข้อมูลลูกค้า", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("ชื่อ") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("นามสกุล") },
                            singleLine = true
                        )
                    }
                    if (creditSelected && !customerValid) {
                        Text(
                            "การขายเงินเชื่อต้องกรอกชื่อและนามสกุลลูกค้า",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item { Divider() }
            item { Text("3 ตัว", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            itemsIndexed(threeDigitEntries, key = { _, entry -> "three-${entry.id}" }) { index, entry ->
                SaleNumberRow(
                    digitCount = 3,
                    firstDigit = entry.firstDigit,
                    onFirstDigitChange = {
                        threeDigitEntries[index] = entry.copy(firstDigit = it)
                        refreshQuotaHint("three-${entry.id}-straight", it + entry.secondDigit + entry.thirdDigit, PrizeType.THREE_DIGIT_STRAIGHT.name, 3)
                        refreshQuotaHint("three-${entry.id}-tod", it + entry.secondDigit + entry.thirdDigit, PrizeType.THREE_DIGIT_TOD.name, 3)
                    },
                    secondDigit = entry.secondDigit,
                    onSecondDigitChange = {
                        threeDigitEntries[index] = entry.copy(secondDigit = it)
                        refreshQuotaHint("three-${entry.id}-straight", entry.firstDigit + it + entry.thirdDigit, PrizeType.THREE_DIGIT_STRAIGHT.name, 3)
                        refreshQuotaHint("three-${entry.id}-tod", entry.firstDigit + it + entry.thirdDigit, PrizeType.THREE_DIGIT_TOD.name, 3)
                    },
                    thirdDigit = entry.thirdDigit,
                    onThirdDigitChange = {
                        threeDigitEntries[index] = entry.copy(thirdDigit = it)
                        val number = entry.firstDigit + entry.secondDigit + it
                        refreshQuotaHint("three-${entry.id}-straight", number, PrizeType.THREE_DIGIT_STRAIGHT.name, 3)
                        refreshQuotaHint("three-${entry.id}-tod", number, PrizeType.THREE_DIGIT_TOD.name, 3)
                    },
                    firstAmountLabel = "ตรง",
                    firstAmount = entry.straightAmount,
                    onFirstAmountChange = { threeDigitEntries[index] = entry.copy(straightAmount = it) },
                    secondAmountLabel = "โต๊ด",
                    secondAmount = entry.boxAmount,
                    onSecondAmountChange = { threeDigitEntries[index] = entry.copy(boxAmount = it) },
                    quotaHint = listOf(
                        quotaHints["three-${entry.id}-straight"],
                        quotaHints["three-${entry.id}-tod"]
                    ).filterNotNull().distinct().joinToString(" · ").takeIf { it.isNotBlank() }
                )
            }
            item {
                TextButton(onClick = { requestAddThree() }) { Text("+ เพิ่มรายการ 3 ตัว") }
            }
            item { Divider() }
            item { Text("2 ตัว", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            itemsIndexed(twoDigitEntries, key = { _, entry -> "two-${entry.id}" }) { index, entry ->
                SaleNumberRow(
                    digitCount = 2,
                    firstDigit = entry.firstDigit,
                    onFirstDigitChange = {
                        twoDigitEntries[index] = entry.copy(firstDigit = it)
                        refreshQuotaHint("two-${entry.id}-top", it + entry.secondDigit, PrizeType.TWO_DIGIT_TOP.name, 2)
                        refreshQuotaHint("two-${entry.id}-bottom", it + entry.secondDigit, PrizeType.TWO_DIGIT_BOTTOM.name, 2)
                    },
                    secondDigit = entry.secondDigit,
                    onSecondDigitChange = {
                        twoDigitEntries[index] = entry.copy(secondDigit = it)
                        val number = entry.firstDigit + it
                        refreshQuotaHint("two-${entry.id}-top", number, PrizeType.TWO_DIGIT_TOP.name, 2)
                        refreshQuotaHint("two-${entry.id}-bottom", number, PrizeType.TWO_DIGIT_BOTTOM.name, 2)
                    },
                    firstAmountLabel = "บน",
                    firstAmount = entry.topAmount,
                    onFirstAmountChange = { twoDigitEntries[index] = entry.copy(topAmount = it) },
                    secondAmountLabel = "ล่าง",
                    secondAmount = entry.bottomAmount,
                    onSecondAmountChange = { twoDigitEntries[index] = entry.copy(bottomAmount = it) },
                    quotaHint = listOf(
                        quotaHints["two-${entry.id}-top"],
                        quotaHints["two-${entry.id}-bottom"]
                    ).filterNotNull().distinct().joinToString(" · ").ifBlank { null }
                )
            }
            item {
                TextButton(onClick = { requestAddTwo() }) { Text("+ เพิ่มรายการ 2 ตัว") }
            }
            item { Divider() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("การชำระเงิน", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = cashSelected, onCheckedChange = { cashSelected = it })
                        Text("เงินสด")
                        Checkbox(checked = creditSelected, onCheckedChange = { creditSelected = it })
                        Text("เงินเชื่อ")
                    }
                    if (cashSelected && creditSelected) {
                        Text("แบ่งชำระ", style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = cashAmount,
                            onValueChange = { cashAmount = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("จำนวนเงินสด (บาท)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = creditAmount,
                            onValueChange = { creditAmount = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("จำนวนเงินเชื่อ (บาท)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        if (!splitValid) {
                            Text(
                                "ยอดเงินสดและเงินเชื่อต้องรวมกันเท่ากับยอดซื้อ",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (hasNoAcceptItems) {
                        Text(
                            "มีเลขที่ตั้งค่าไม่รับอยู่ในรายการ กรุณาเปลี่ยนเลขก่อนบันทึก",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!activeEntriesValid) {
                        Text(
                            "กรุณากรอกเลขให้ครบทุกหลักในรายการที่มียอดซื้อ",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
