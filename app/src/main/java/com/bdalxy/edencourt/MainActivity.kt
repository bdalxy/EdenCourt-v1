package com.bdalxy.edencourt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bdalxy.edencourt.core.ui.theme.EdenCourtTheme
import com.bdalxy.edencourt.navigation.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdenCourtTheme {
                AppRoot()
            }
        }
    }
}