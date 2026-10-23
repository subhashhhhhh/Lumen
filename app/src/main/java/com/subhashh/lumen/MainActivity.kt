package com.subhashh.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.subhashh.lumen.ui.LumenApp
import com.subhashh.lumen.ui.setLumenContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLumenContent { LumenApp() }
    }
}
