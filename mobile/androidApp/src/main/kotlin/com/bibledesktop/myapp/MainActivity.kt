package com.bibledesktop.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bibledesktop.myapp.ui.setup.SetupApp
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BibleDesktopTheme {
                SetupApp()
            }
        }
    }
}
