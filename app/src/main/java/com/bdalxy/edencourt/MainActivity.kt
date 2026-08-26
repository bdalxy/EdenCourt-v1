package com.bdalxy.edencourt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bdalxy.edencourt.core.ui.theme.EdenCourtTheme
import com.bdalxy.edencourt.feature.onboarding.OnboardingGate
import com.bdalxy.edencourt.navigation.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdenCourtTheme {
                // T11.14：首启引导门——首次启动先展示 2~3 步引导，看完/跳过后再进入主界面
                OnboardingGate {
                    AppRoot()
                }
            }
        }
    }
}