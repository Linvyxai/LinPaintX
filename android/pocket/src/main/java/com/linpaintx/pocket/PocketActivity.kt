package com.linpaintx.pocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * LinPaintX Pocket: a simplified painting app for phones.
 *
 * This is a placeholder. The full implementation will reuse the core engine
 * from :app with a phone-optimized UI.
 */
class PocketActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketApp()
        }
    }
}

@Composable
fun PocketApp() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("LinPaintX Pocket\nВ разработке")
    }
}
