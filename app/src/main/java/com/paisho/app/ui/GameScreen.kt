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
import com.paisho.core.game.BoardZone
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GamePhase
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.TileType
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.min
import kotlin.math.sqrt

@Composable
fun PaiShoApp(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
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
                    title = { Text("Skud Pai Sho v0.0.03") },
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
        Text("2) Choose exactly 4 Accent tiles (max 2 of each)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accentChoices.forEach { accent ->
                val count = setup.selectedAccents.count { it == accent }
                TextButton(onClick = { onAccentToggled(accent) }) {
                    Text("${accent.shortName} ($count/2)")
                }
            }
        }
        Text("Selected: ${setup.selectedAccents.size}/4")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreateGame, enabled = setup.canStart) {
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
            coordinateExtent = state.coordinateExtent,
            selectedSource = state.selectedSource,
            selectedTarget = state.selectedTarget,
            legalTargets = state.legalTargets,
            legalPositions = state.legalPositions,
            zoneByPosition = state.zoneByPosition,
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
    coordinateExtent: Int,
    selectedSource: Position?,
    selectedTarget: Position?,
    legalTargets: Set<Position>,
    legalPositions: Set<Position>,
    zoneByPosition: Map<Position, BoardZone>,
    onTileClick: (Position) -> Unit,
    cells: Map<Position, String>
) {
    val boardSizeDp = 360.dp
    val pointTouchRadius = 13.dp
    val pointVisualRadius = 3.dp
    val latticeRadiusFraction = 0.44f
    val boardRadiusFraction = 0.48f
    val playableRadiusFraction = 0.96f

    fun intersectionFraction(position: Position): Pair<Float, Float> {
        val nx = if (coordinateExtent == 0) 0f else (position.col.toFloat() / coordinateExtent.toFloat())
        val ny = if (coordinateExtent == 0) 0f else (-position.row.toFloat() / coordinateExtent.toFloat())
        return (0.5f + nx * latticeRadiusFraction) to (0.5f + ny * latticeRadiusFraction)
    }

    val allPoints = legalPositions.sortedWith(compareByDescending<Position> { it.row }.thenBy { it.col })
    val interactivePoints = (legalTargets + selectedSource + selectedTarget).filterNotNull().toSet()

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
                    for (point in allPoints) {
                        val (fx, fy) = intersectionFraction(point)
                        val p = Offset(size.width * fx, size.height * fy)
                        val px = tapOffset.x - p.x
                        val py = tapOffset.y - p.y
                        val d = sqrt(px * px + py * py)
                        if (d < closestDistance) {
                            closestDistance = d
                            closest = point
                        }
                    }
                    // Tight snapping: only accept taps close to a legal intersection marker.
                    val snapRadiusPx = (sizePx / boardSize) * 0.32f
                    if (closest != null && closestDistance <= snapRadiusPx) {
                        onTileClick(closest)
                    }
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sizePx = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = sizePx * boardRadiusFraction

            drawCircle(color = Color(0xFF3A3638), radius = radius, center = center, style = Fill)
            drawCircle(color = Color(0xFFD8D0C9), radius = radius * 0.965f, center = center, style = Fill)

            // Paint board zones directly from legal coordinate map.
            val cellRadius = maxOf(1.6f, (sizePx / boardSize) * 0.38f)
            for (point in allPoints) {
                val (fx, fy) = intersectionFraction(point)
                val marker = Offset(size.width * fx, size.height * fy)
                val zoneColor = when (zoneByPosition[point]) {
                    BoardZone.BORDER -> Color(0xFF2E2E32)
                    BoardZone.GATE -> Color(0xFF6C9D7A)
                    BoardZone.RED_GARDEN -> Color(0xFFD86A63)
                    BoardZone.WHITE_GARDEN -> Color(0xFFF2F0EC)
                    BoardZone.NEUTRAL_GARDEN -> Color(0xFFD2C7B8)
                    null -> Color.Transparent
                }
                drawCircle(color = zoneColor, radius = cellRadius, center = marker, style = Fill)
            }

            // Draw board lattice lines between legal intersections, so pieces sit on line crossings.
            for (row in coordinateExtent downTo -coordinateExtent) {
                var prev: Offset? = null
                for (col in -coordinateExtent..coordinateExtent) {
                    val position = Position(row, col)
                    if (position !in legalPositions) {
                        prev = null
                        continue
                    }
                    val (fx, fy) = intersectionFraction(position)
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
            for (col in -coordinateExtent..coordinateExtent) {
                var prev: Offset? = null
                for (row in coordinateExtent downTo -coordinateExtent) {
                    val position = Position(row, col)
                    if (position !in legalPositions) {
                        prev = null
                        continue
                    }
                    val (fx, fy) = intersectionFraction(position)
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
            for (point in allPoints) {
                val (fx, fy) = intersectionFraction(point)
                val marker = Offset(size.width * fx, size.height * fy)
                drawCircle(color = Color(0xFF1F1A1B), radius = 2.2f, center = marker)
            }
            drawCircle(color = Color(0xFF2D2527), radius = radius * 0.995f, center = center, style = Stroke(width = 4f))
        }

        allPoints.forEach { position ->
            val token = cells[position].orEmpty()
            val isSource = selectedSource == position
            val isTarget = selectedTarget == position
            val isLegalTarget = position in legalTargets
            val isInteractiveHint = position in interactivePoints
            if (token.isEmpty() && !isSource && !isTarget && !isLegalTarget && !isInteractiveHint) return@forEach

            val (fx, fy) = intersectionFraction(position)
            val x = boardSizeDp * fx
            val y = boardSizeDp * fy

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = x - pointTouchRadius, y = y - pointTouchRadius)
                    .size(pointTouchRadius * 2)
                    .background(
                        when {
                            isSource -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            isTarget -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                            isLegalTarget -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            isInteractiveHint -> Color(0x22000000)
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
