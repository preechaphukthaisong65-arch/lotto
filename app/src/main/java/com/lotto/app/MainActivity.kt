package com.lotto.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lotto.app.ui.HomeScreen
import com.lotto.app.ui.sale.SaleScreen

private enum class AppScreen {
    Home,
    Sale
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Home.name) }
                Surface(modifier = Modifier) {
                    when (currentScreen) {
                        AppScreen.Sale.name -> SaleScreen(
                            onBack = { currentScreen = AppScreen.Home.name },
                            onSave = {
                                Toast.makeText(
                                    this,
                                    "หน้าขายยังอยู่ระหว่างพัฒนา ข้อมูลยังไม่ได้บันทึก",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                        else -> HomeScreen(
                            onOpenSale = { currentScreen = AppScreen.Sale.name }
                        )
                    }
                }
            }
        }
    }
}
