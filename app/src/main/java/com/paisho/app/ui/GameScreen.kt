package com.paisho.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paisho.core.game.GamePhase
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.TileType

@Composable
fun PaiShoApp(viewModel: GameViewModel = viewModel()) {
    GameScreen(viewModel = viewModel)
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Skud Pai Sho v0.0.01",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (state.isGameOver) {
                when {
                    state.isDraw -> "Game over: draw (${state.endReason?.name ?: "resolved"})."
                    state.winner == Player.HUMAN -> "Game over: you win (${state.endReason?.name ?: "resolved"})."
                    state.winner == Player.AI -> "Game over: AI wins (${state.endReason?.name ?: "resolved"})."
                    else -> "Game over."
                }
            } else {
                "Turn: ${if (state.currentPlayer == Player.HUMAN) "Human" else "AI"} | Rules: full parity core"
            }
        )

        Board(
            boardSize = state.boardSize,
            selectedSource = state.selectedSource,
            selectedTarget = state.selectedTarget,
            legalTargets = state.legalTargets,
            onTileClick = viewModel::onPositionSelected,
            cells = state.boardSnapshot
        )

        Text(
            text = when {
                state.phase == GamePhase.FINISHED -> when (state.endReason) {
                    GameEndReason.HARMONY_RING -> "Victory by Harmony Ring."
                    GameEndReason.LAST_BASIC_PLAYED -> "End by last Basic tile and midline harmonies."
                    null -> "Game finished."
                }
                state.selectedSource != null -> "Arrange source: (${state.selectedSource.row}, ${state.selectedSource.col}) | target: ${state.selectedTarget?.let { "(${it.row}, ${it.col})" } ?: "none"}"
                else -> "Plant tile: ${state.selectedTileType?.name ?: "none"} | Gate: ${state.selectedTarget?.let { "(${it.row}, ${it.col})" } ?: "none"}"
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TilePickerButton(
                label = "R3 Rose",
                selected = state.selectedTileType == TileType.ROSE,
                onClick = { viewModel.selectTileToPlant(TileType.ROSE) }
            )
            TilePickerButton(
                label = "R4 Chrys",
                selected = state.selectedTileType == TileType.CHRYSANTHEMUM,
                onClick = { viewModel.selectTileToPlant(TileType.CHRYSANTHEMUM) }
            )
            TilePickerButton(
                label = "R5 Rhodo",
                selected = state.selectedTileType == TileType.RHODODENDRON,
                onClick = { viewModel.selectTileToPlant(TileType.RHODODENDRON) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TilePickerButton(
                label = "W3 Jasmine",
                selected = state.selectedTileType == TileType.JASMINE,
                onClick = { viewModel.selectTileToPlant(TileType.JASMINE) }
            )
            TilePickerButton(
                label = "W4 Lily",
                selected = state.selectedTileType == TileType.LILY,
                onClick = { viewModel.selectTileToPlant(TileType.LILY) }
            )
            TilePickerButton(
                label = "W5 Jade",
                selected = state.selectedTileType == TileType.WHITE_JADE,
                onClick = { viewModel.selectTileToPlant(TileType.WHITE_JADE) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::performSelectedMoveOrPlant, enabled = !state.isGameOver) {
                Text("Apply Turn")
            }
            Button(onClick = viewModel::resetGame) {
                Text("Reset")
            }
        }

        Text("Game log", fontWeight = FontWeight.SemiBold)
        Card(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(modifier = Modifier.height(180.dp).padding(8.dp)) {
                items(state.eventLog) { entry ->
                    Text(text = entry, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun Board(
    boardSize: Int,
    selectedSource: Position?,
    selectedTarget: Position?,
    legalTargets: Set<Position>,
    onTileClick: (Position) -> Unit,
    cells: Map<Position, String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(boardSize) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(boardSize) { col ->
                    val position = Position(row, col)
                    val token = cells[position].orEmpty()
                    val isSource = selectedSource == position
                    val isTarget = selectedTarget == position
                    val isLegalTarget = position in legalTargets
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                when {
                                    isSource -> MaterialTheme.colorScheme.primaryContainer
                                    isTarget -> MaterialTheme.colorScheme.tertiaryContainer
                                    isLegalTarget -> MaterialTheme.colorScheme.secondaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable { onTileClick(position) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = token,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (token.startsWith("A")) Color(0xFFB71C1C) else Color(0xFF1A237E)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TilePickerButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier,
        enabled = true
    ) {
        Text(if (selected) "[$label]" else label)
    }
}
