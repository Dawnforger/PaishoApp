package com.paisho.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PaiShoApp(viewModel: GameViewModel = viewModel()) {
    GameScreen(viewModel = viewModel)
}
