package com.paisho.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.paisho.app.ui.PaiShoApp
import com.paisho.app.ui.theme.PaiShoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaiShoTheme {
                PaiShoApp()
            }
        }
    }
}
