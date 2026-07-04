package com.mejoresiagratis.rellenador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mejoresiagratis.rellenador.ui.RellenadorNavHost
import com.mejoresiagratis.rellenador.ui.theme.RellenadorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RellenadorTheme {
                RellenadorNavHost()
            }
        }
    }
}
