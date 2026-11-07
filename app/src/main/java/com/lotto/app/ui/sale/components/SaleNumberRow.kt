package com.lotto.app.ui.sale.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun SaleNumberRow(
    digitCount: Int,
    firstDigit: String,
    onFirstDigitChange: (String) -> Unit,
    secondDigit: String,
    onSecondDigitChange: (String) -> Unit,
    thirdDigit: String = "",
    onThirdDigitChange: (String) -> Unit = {},
    firstAmountLabel: String,
    firstAmount: String,
    onFirstAmountChange: (String) -> Unit,
    secondAmountLabel: String,
    secondAmount: String,
    onSecondAmountChange: (String) -> Unit,
    quotaHint: String? = null
) {
    val firstFocus = remember { FocusRequester() }
    val secondFocus = remember { FocusRequester() }
    val thirdFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (quotaHint != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 1.dp
            ) {
                Text(
                    text = quotaHint,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        DigitField(
            label = "เลข 1",
            value = firstDigit,
            onValueChange = { value ->
                onFirstDigitChange(value)
                if (value.length == 1) secondFocus.requestFocus()
            },
            modifier = Modifier.weight(0.8f),
            focusRequester = firstFocus
        )
        DigitField(
            label = "เลข 2",
            value = secondDigit,
            onValueChange = { value ->
                onSecondDigitChange(value)
                if (value.length == 1 && digitCount == 3) thirdFocus.requestFocus()
            },
            modifier = Modifier.weight(0.8f),
            focusRequester = secondFocus
        )
        if (digitCount == 3) {
            DigitField(
                label = "เลข 3",
                value = thirdDigit,
                onValueChange = onThirdDigitChange,
                modifier = Modifier.weight(0.8f),
                focusRequester = thirdFocus
            )
        }
        AmountField(firstAmountLabel, firstAmount, onFirstAmountChange, Modifier.weight(1.25f))
        AmountField(secondAmountLabel, secondAmount, onSecondAmountChange, Modifier.weight(1.25f))
        }
    }
}

@Composable
private fun DigitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    focusRequester: FocusRequester
) {
    Column(modifier = modifier) {
        Text(text = label)
        OutlinedTextField(
            value = value,
            onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(1)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        Text(text = label)
        OutlinedTextField(
            value = value,
            onValueChange = { input -> onValueChange(input.filter(Char::isDigit)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
