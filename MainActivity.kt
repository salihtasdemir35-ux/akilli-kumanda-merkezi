package com.akillikumanda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.akillikumanda.app.core.designsystem.AkilliKumandaTheme
import com.akillikumanda.app.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AkilliKumandaTheme { AppNavHost() } }
    }
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as AkilliKumandaApp).container
