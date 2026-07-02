package com.alas.md3gpscam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.alas.md3gpscam.ui.MainScreen
import com.alas.md3gpscam.ui.MainViewModel
import com.alas.md3gpscam.ui.theme.GpsCammaterialUiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val dynamicColor by viewModel.dynamicColorsEnabled.collectAsState()

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            GpsCammaterialUiTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}