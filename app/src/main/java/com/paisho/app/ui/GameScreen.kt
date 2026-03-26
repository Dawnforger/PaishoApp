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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sqrt

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
    val pointTouchRadius = 13.dp
    val pointVisualRadius = 3.dp
    val latticeRadiusFraction = 0.44f
    val boardRadiusFraction = 0.48f
    val playableRadiusFraction = 0.96f

    fun intersectionFraction(row: Int, col: Int): Pair<Float, Float> {
        val nx = if (boardSize <= 1) 0f else (col.toFloat() / (boardSize - 1)) * 2f - 1f
        val ny = if (boardSize <= 1) 0f else (row.toFloat() / (boardSize - 1)) * 2f - 1f
        return (0.5f + nx * latticeRadiusFraction) to (0.5f + ny * latticeRadiusFraction)
    }

    Box(
        modifier = Modifier
            .size(boardSizeDp)
            .pointerInput(boardSize, legalTargets) {
                detectTapGestures { tapOffset ->
                    val sizePx = min(size.width, size.height)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val boardRadius = sizePx * boardRadiusFraction
                    val playableRadius = boardRadius * playableRadiusFraction
                    val dx = tapOffset.x - center.x
                    val dy = tapOffset.y - center.y
                    if (dx * dx + dy * dy > playableRadius * playableRadius) return@detectTapGestures

                    var closest: Position? = null
                    var closestDistance = Float.MAX_VALUE
                    for (row in 0 until boardSize) {
                        for (col in 0 until boardSize) {
                            val (fx, fy) = intersectionFraction(row, col)
                            val p = Offset(size.width * fx, size.height * fy)
                            val px = tapOffset.x - p.x
                            val py = tapOffset.y - p.y
                            val d = sqrt(px * px + py * py)
                            if (d < closestDistance) {
                                closestDistance = d
                                closest = Position(row, col)
                            }
                        }
                    }
                    // Tight snapping: only accept taps close to a legal intersection marker.
                    val snapRadiusPx = (sizePx / boardSize) * 0.32f
                    if (closest != null && closestDistance <= snapRadiusPx) {
                        onTileClick(closest)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sizePx = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = sizePx * boardRadiusFraction

            drawCircle(color = Color(0xFF3A3638), radius = radius, center = center, style = Fill)
            drawCircle(color = Color(0xFFD8D0C9), radius = radius * 0.965f, center = center, style = Fill)

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

            // Draw board lattice lines between intersections, so pieces sit on line crossings.
            for (row in 0 until boardSize) {
                var prev: Offset? = null
                for (col in 0 until boardSize) {
                    val (fx, fy) = intersectionFraction(row, col)
                    val point = Offset(size.width * fx, size.height * fy)
                    prev?.let {
                        drawLine(
                            color = Color(0xFF1F1A1B),
                            start = it,
                            end = point,
                            strokeWidth = 1.4f,
                            cap = StrokeCap.Round
                        )
                    }
                    prev = point
                }
            }
            for (col in 0 until boardSize) {
                var prev: Offset? = null
                for (row in 0 until boardSize) {
                    val (fx, fy) = intersectionFraction(row, col)
                    val point = Offset(size.width * fx, size.height * fy)
                    prev?.let {
                        drawLine(
                            color = Color(0xFF1F1A1B),
                            start = it,
                            end = point,
                            strokeWidth = 1.4f,
                            cap = StrokeCap.Round
                        )
                    }
                    prev = point
                }
            }
            for (row in 0 until boardSize) {
                for (col in 0 until boardSize) {
                    val (fx, fy) = intersectionFraction(row, col)
                    val point = Offset(size.width * fx, size.height * fy)
                    drawCircle(color = Color(0xFF1F1A1B), radius = 2.2f, center = point)
                }
            }
            drawCircle(color = Color(0xFF2D2527), radius = radius * 0.995f, center = center, style = Stroke(width = 4f))
        }

        repeat(boardSize) { row ->
            repeat(boardSize) { col ->
                val position = Position(row, col)
                val token = cells[position].orEmpty()
                val isSource = selectedSource == position
                val isTarget = selectedTarget == position
                val isLegalTarget = position in legalTargets

                val (fx, fy) = intersectionFraction(row, col)
                val x = boardSizeDp * fx
                val y = boardSizeDp * fy

                Box(
                    modifier = Modifier
                        .offset(x = x - pointTouchRadius, y = y - pointTouchRadius)
                        .size(pointTouchRadius * 2)
                        .background(
                            when {
                                isSource -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                isTarget -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                isLegalTarget -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                else -> Color.Transparent
                            },
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (token.isNotEmpty()) {
                        Text(
                            text = token,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (token.startsWith("A")) Color(0xFF7A1113) else Color(0xFF15264A)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(pointVisualRadius * 2)
                                .background(Color(0xFF1F1A1B), shape = androidx.compose.foundation.shape.CircleShape)
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
