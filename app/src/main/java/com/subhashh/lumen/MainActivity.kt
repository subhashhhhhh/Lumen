package com.subhashh.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.subhashh.lumen.ui.LumenApp
import com.subhashh.lumen.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LumenTheme {
                LumenApp()
            }
        }
    }
}
