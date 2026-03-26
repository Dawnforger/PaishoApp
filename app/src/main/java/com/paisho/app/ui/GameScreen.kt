@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.paisho.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import com.paisho.core.game.AccentType
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GamePhase
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.TileType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun PaiShoApp(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Pai Sho",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                NavigationDrawerItem(
                    label = { Text("Game") },
                    selected = state.drawerSection == DrawerSection.Game,
                    onClick = {
                        viewModel.openHome()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Existing Games") },
                    selected = state.drawerSection == DrawerSection.ExistingGames,
                    onClick = {
                        viewModel.openExistingGames()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = state.drawerSection == DrawerSection.Settings,
                    onClick = {
                        viewModel.openSettings()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Skud Pai Sho v0.0.01") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (state.appScreen) {
                    AppScreen.Home -> HomeScreen(onNewGame = viewModel::startNewGameFlow)
                    AppScreen.NewGameSetup -> NewGameSetupScreen(
                        setup = state.setupState,
                        onOpeningTileSelected = viewModel::toggleOpeningTile,
                        onAccentToggled = viewModel::toggleAccentSelection,
                        onCreateGame = viewModel::createNewGameFromSetup,
                        onBack = viewModel::openHome
                    )
                    AppScreen.Game -> GameScreen(viewModel = viewModel)
                    AppScreen.ExistingGames -> ExistingGamesScreen(state.existingGames)
                    AppScreen.Settings -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onNewGame: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("Welcome to Pai Sho", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Start a new game to choose opening tile and accent loadout.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onNewGame) { Text("New Game") }
    }
}

@Composable
private fun NewGameSetupScreen(
    setup: NewGameSetupState,
    onOpeningTileSelected: (TileType) -> Unit,
    onAccentToggled: (AccentType) -> Unit,
    onCreateGame: () -> Unit,
    onBack: () -> Unit
) {
    val accentChoices = listOf(AccentType.ROCK, AccentType.WHEEL, AccentType.KNOTWEED, AccentType.BOAT)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("New Game Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("1) Choose opening Basic Flower tile", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TileType.ROSE, TileType.CHRYSANTHEMUM, TileType.RHODODENDRON,
                TileType.JASMINE, TileType.LILY, TileType.WHITE_JADE
            ).forEach { tile ->
                TextButton(onClick = { onOpeningTileSelected(tile) }) {
                    Text(if (setup.openingBasicType == tile) "[${tile.shortName}]" else tile.shortName)
                }
            }
        }
        HorizontalDivider()
        Text("2) Choose exactly 4 Accent tiles", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accentChoices.forEach { accent ->
                TextButton(onClick = { onAccentToggled(accent) }) {
                    Text(if (accent in setup.selectedAccents) "[${accent.shortName}]" else accent.shortName)
                }
            }
        }
        Text("Selected: ${setup.selectedAccents.size}/4")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreateGame, enabled = setup.selectedAccents.size == 4) {
                Text("Create Game")
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(
            "Gameplay flow: opening tiles are placed in opposite gates, then turns proceed with Plant or Arrange per rules.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ExistingGamesScreen(existingGames: List<ExistingGameSummary>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Existing Games", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (existingGames.isEmpty()) {
            Text("No saved games yet.")
        } else {
            LazyColumn {
                items(existingGames) { game ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(game.title, fontWeight = FontWeight.SemiBold)
                            Text(game.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Settings menu scaffolded for upcoming preferences:")
        Text("- Enable move hints")
        Text("- Toggle harmony overlays")
        Text("- Theme selection")
    }
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
            text = if (state.isGameOver) {
                when {
                    state.isDraw -> "Game over: draw (${state.endReason?.name ?: "resolved"})."
                    state.winner == Player.HUMAN -> "Game over: you win (${state.endReason?.name ?: "resolved"})."
                    state.winner == Player.AI -> "Game over: AI wins (${state.endReason?.name ?: "resolved"})."
                    else -> "Game over."
                }
            } else {
                "Turn: ${if (state.currentPlayer == Player.HUMAN) "Human" else "AI"}"
            }
        )

        CircularBoard(
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
                    GameEndReason.FORCED_DRAW -> "Game ended in a forced draw."
                    null -> "Game finished."
                }
                state.selectedSource != null -> {
                    val source = state.selectedSource
                    "Arrange source: (${source?.row}, ${source?.col}) | target: ${state.selectedTarget?.let { "(${it.row}, ${it.col})" } ?: "none"}"
                }
                else -> "Plant tile: ${state.selectedTileType?.name ?: "none"} | Gate: ${state.selectedTarget?.let { "(${it.row}, ${it.col})" } ?: "none"}"
            },
            style = MaterialTheme.typography.bodyMedium
        )

        LazyColumn(modifier = Modifier.height(74.dp)) {
            items(listOf(TileType.ROSE, TileType.CHRYSANTHEMUM, TileType.RHODODENDRON, TileType.JASMINE, TileType.LILY, TileType.WHITE_JADE)) { tile ->
                TilePickerButton(
                    label = tile.shortName,
                    selected = state.selectedTileType == tile,
                    onClick = { viewModel.selectTileToPlant(tile) }
                )
            }
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
            LazyColumn(modifier = Modifier.height(160.dp).padding(8.dp)) {
                items(state.eventLog) { entry ->
                    Text(text = entry, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun CircularBoard(
    boardSize: Int,
    selectedSource: Position?,
    selectedTarget: Position?,
    legalTargets: Set<Position>,
    onTileClick: (Position) -> Unit,
    cells: Map<Position, String>
) {
    val boardSizeDp = 360.dp
    Box(modifier = Modifier.size(boardSizeDp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sizePx = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = sizePx * 0.48f
            val gridStep = (radius * 2f) / (boardSize + 1)

            drawCircle(color = Color(0xFF3A3638), radius = radius, center = center, style = Fill)
            drawCircle(color = Color(0xFFD8D0C9), radius = radius * 0.965f, center = center, style = Fill)

            fun insideCircle(p: Offset): Boolean {
                val dx = p.x - center.x
                val dy = p.y - center.y
                return dx * dx + dy * dy <= (radius * 0.96f) * (radius * 0.96f)
            }

            for (i in -boardSize..boardSize) {
                val offset = i * gridStep * 0.72f
                val startA = Offset(center.x - radius, center.y + offset)
                val endA = Offset(center.x + radius, center.y + offset)
                drawLine(
                    color = Color(0xFF1F1A1B),
                    start = startA,
                    end = endA,
                    strokeWidth = 1.4f,
                    cap = StrokeCap.Round
                )

                val startB = Offset(center.x + offset, center.y - radius)
                val endB = Offset(center.x + offset, center.y + radius)
                drawLine(
                    color = Color(0xFF1F1A1B),
                    start = startB,
                    end = endB,
                    strokeWidth = 1.4f,
                    cap = StrokeCap.Round
                )
            }

            // Diagonal crosshatch grid similar to the reference style.
            for (i in -boardSize..boardSize) {
                val offset = i * gridStep * 0.72f
                drawLine(
                    color = Color(0xFF1F1A1B),
                    start = Offset(center.x - radius, center.y - radius + offset),
                    end = Offset(center.x + radius, center.y + radius + offset),
                    strokeWidth = 1.2f
                )
                drawLine(
                    color = Color(0xFF1F1A1B),
                    start = Offset(center.x - radius, center.y + radius + offset),
                    end = Offset(center.x + radius, center.y - radius + offset),
                    strokeWidth = 1.2f
                )
            }

            val centralPath = Path().apply {
                val top = Offset(center.x, center.y - radius * 0.52f)
                val left = Offset(center.x - radius * 0.52f, center.y)
                val right = Offset(center.x + radius * 0.52f, center.y)
                val bottom = Offset(center.x, center.y + radius * 0.52f)
                moveTo(top.x, top.y)
                lineTo(left.x, left.y)
                lineTo(center.x, center.y)
                lineTo(top.x, top.y)
                moveTo(top.x, top.y)
                lineTo(right.x, right.y)
                lineTo(center.x, center.y)
                lineTo(top.x, top.y)
                moveTo(bottom.x, bottom.y)
                lineTo(left.x, left.y)
                lineTo(center.x, center.y)
                lineTo(bottom.x, bottom.y)
                moveTo(bottom.x, bottom.y)
                lineTo(right.x, right.y)
                lineTo(center.x, center.y)
                lineTo(bottom.x, bottom.y)
            }
            drawPath(path = centralPath, color = Color(0xFFA1262A), style = Fill)
            drawCircle(color = Color(0xFF2D2527), radius = radius * 0.995f, center = center, style = Stroke(width = 4f))
        }

        val radiusFraction = 0.46f
        val centerIndex = (boardSize - 1) / 2f
        repeat(boardSize) { row ->
            repeat(boardSize) { col ->
                val position = Position(row, col)
                val token = cells[position].orEmpty()
                val isSource = selectedSource == position
                val isTarget = selectedTarget == position
                val isLegalTarget = position in legalTargets

                // Map board indices to circular coordinates.
                val dx = (col - centerIndex) / centerIndex
                val dy = (row - centerIndex) / centerIndex
                val r = kotlin.math.sqrt(dx * dx + dy * dy)
                if (r > 1.02f) return@repeat
                val angle = kotlin.math.atan2(dy, dx)
                val projectedX = 0.5f + cos(angle).toFloat() * (r * radiusFraction)
                val projectedY = 0.5f + sin(angle).toFloat() * (r * radiusFraction)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(
                                start = boardSizeDp * projectedX - 13.dp,
                                top = boardSizeDp * projectedY - 13.dp
                            )
                            .size(26.dp)
                            .background(
                                when {
                                    isSource -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    isTarget -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                    isLegalTarget -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { onTileClick(position) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (token.isNotEmpty()) {
                            Text(
                                text = token,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (token.startsWith("A")) Color(0xFF7A1113) else Color(0xFF15264A)
                            )
                        }
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
