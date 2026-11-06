package com.lotto.app.ui.sale.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
    onSecondAmountChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DigitField("เลข 1", firstDigit, onFirstDigitChange, Modifier.weight(0.8f))
        DigitField("เลข 2", secondDigit, onSecondDigitChange, Modifier.weight(0.8f))
        if (digitCount == 3) {
            DigitField("เลข 3", thirdDigit, onThirdDigitChange, Modifier.weight(0.8f))
        }
        AmountField(firstAmountLabel, firstAmount, onFirstAmountChange, Modifier.weight(1.25f))
        AmountField(secondAmountLabel, secondAmount, onSecondAmountChange, Modifier.weight(1.25f))
    }
}

@Composable
private fun DigitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        Text(text = label)
        OutlinedTextField(
            value = value,
            onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(1)) },
            modifier = Modifier.fillMaxWidth(),
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
