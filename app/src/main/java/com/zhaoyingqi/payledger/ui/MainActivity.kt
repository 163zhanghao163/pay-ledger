package com.zhaoyingqi.payledger.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.zhaoyingqi.payledger.ui.theme.PayLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PayLedgerTheme { Text("PayLedger") } }
    }
}
