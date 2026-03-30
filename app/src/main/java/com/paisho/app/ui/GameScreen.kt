@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.paisho.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalLayoutApi::class)
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
                    title = { Text("Skud Pai Sho v0.0.12") },
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
                    AppScreen.ExistingGames -> ExistingGamesScreen(
                        existingGames = state.existingGames,
                        onResume = viewModel::resumeGame
                    )
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
private fun ExistingGamesScreen(
    existingGames: List<ExistingGameSummary>,
    onResume: (String) -> Unit
) {
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onResume(game.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(game.title, fontWeight = FontWeight.SemiBold)
                            Text(game.subtitle, style = MaterialTheme.typography.bodySmall)
                            Text("Tap to resume", style = MaterialTheme.typography.labelSmall)
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularBoard(
                    boardSize = state.boardSize,
                    coordinateExtent = state.coordinateExtent,
                    selectedSource = state.selectedSource,
                    selectedTarget = state.selectedTarget,
                    legalTargets = state.legalTargets,
                    legalPositions = state.legalPositions,
                    zoneByPosition = state.zoneByPosition,
                    boardVisualConfig = state.boardVisualConfig,
                    onTileClick = viewModel::onPositionSelected,
                    cells = state.boardSnapshot
                )
            }
        }

        val selectedTile = state.selectedTileType
        val selectedAccent = state.selectedAccentType

        item {
            Text(
                text = when {
                    state.phase == GamePhase.FINISHED -> when (state.endReason) {
                        GameEndReason.HARMONY_RING -> "Victory by Harmony Ring."
                        GameEndReason.LAST_BASIC_PLAYED -> "End by last Basic tile and midline harmonies."
                        GameEndReason.FORCED_DRAW -> "Game ended in a forced draw."
                        null -> "Game finished."
                    }
                    state.isHarmonyBonusFlow -> "Harmony formed. Select a reserve tile and then a highlighted target for the bonus."
                    state.isAwaitingSubmit -> "Turn action staged. Submit to finalize turn or Undo to revert."
                    state.selectedSource != null -> {
                        val source = state.selectedSource
                        "Piece selected at (${source?.row}, ${source?.col}). Tap a highlighted intersection to move."
                    }
                    selectedTile != null -> "Tile ${tileCode(selectedTile)} selected. Tap a highlighted legal space."
                    selectedAccent != null -> "Accent ${accentCode(selectedAccent)} selected. Bonus placement is available after Harmony."
                    else -> "Select a board piece to move or select a reserve token to plant."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            if (state.isHarmonyBonusFlow) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Harmony bonus active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Choose a legal flower/accent from reserve; legal bonus targets will highlight on the board.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Undo returns to the start of this harmony bonus flow.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::undoTurn,
                    enabled = state.canUndoTurn && !state.isGameOver
                ) {
                    Text("Undo")
                }
                Button(
                    onClick = viewModel::submitTurn,
                    enabled = state.canSubmitTurn && !state.isGameOver
                ) {
                    Text("Submit")
                }
                Button(onClick = viewModel::resetGame) {
                    Text("Reset")
                }
            }
        }

        item {
            if (state.stagedActions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Staged actions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        state.stagedActions.forEachIndexed { index, action ->
                            Text(
                                text = "${index + 1}. $action",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        item {
            ReserveTray(
                state = state,
                onFlowerClick = viewModel::selectFlowerReserveTile,
                onAccentClick = viewModel::selectAccentReserveTile
            )
        }

        item {
            Text("Game log", fontWeight = FontWeight.SemiBold)
        }
        item {
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
    boardVisualConfig: BoardVisualConfig,
    onTileClick: (Position) -> Unit,
    cells: Map<Position, String>
) {
    val boardSizeDp = 360.dp
    val boardRadiusFraction = 0.495f
    val playableRadiusFraction = 0.975f

    val allPoints = legalPositions.sortedWith(compareByDescending<Position> { it.row }.thenBy { it.col })
    val interactivePoints = (legalTargets + selectedSource + selectedTarget).filterNotNull().toSet()

    val sourceHighlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    val targetHighlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .size(boardSizeDp)
            .pointerInput(boardSize, legalTargets) {
                detectTapGestures { tapOffset ->
                    val sizePx = min(size.width, size.height)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val boardRadius = sizePx * boardRadiusFraction
                    val playableRadius = boardRadius * playableRadiusFraction
                    val step = if (coordinateExtent == 0) 0f else (boardRadius * 0.86f) / coordinateExtent.toFloat()
                    val dx = tapOffset.x - center.x
                    val dy = tapOffset.y - center.y
                    if (dx * dx + dy * dy > playableRadius * playableRadius) return@detectTapGestures

                    var closest: Position? = null
                    var closestDistance = Float.MAX_VALUE
                    for (point in allPoints) {
                        val p = Offset(
                            x = center.x + point.col * step,
                            y = center.y - point.row * step,
                        )
                        val px = tapOffset.x - p.x
                        val py = tapOffset.y - p.y
                        val d = sqrt(px * px + py * py)
                        if (d < closestDistance) {
                            closestDistance = d
                            closest = point
                        }
                    }
                    val snapRadiusPx = maxOf(step * 0.55f, 14f)
                    if (closest != null && closestDistance <= snapRadiusPx) {
                        onTileClick(closest)
                    }
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        val boardVisual = boardVisualConfig
        Canvas(modifier = Modifier.matchParentSize().clip(CircleShape)) {
            val sizePx = min(size.width, size.height)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = sizePx * boardRadiusFraction
            val step = if (coordinateExtent == 0) 0f else (radius * 0.86f) / coordinateExtent.toFloat()
            val markerRadius = maxOf(step * 0.12f, 2f)
            val pieceRadiusPx = maxOf(step * 0.37f, 8f)
            val anchorRadiusPx = maxOf(step * 0.46f, pieceRadiusPx + 1f)

            drawCircle(color = Color(0xFF3A3638), radius = radius, center = center, style = Fill)
            drawCircle(color = Color(0xFFD8D0C9), radius = radius * 0.975f, center = center, style = Fill)

            // Placeholder for future image-based board rendering.
            if (boardVisual.backgroundImageResId != null) {
                // Intentionally left as no-op scaffold until drawable loading is wired.
            }

            if (boardVisual.showZoneMarkers) {
                // Paint only non-neutral zone markers from legal coordinate map.
                val cellRadius = maxOf(1.4f, (sizePx / boardSize) * 0.34f)
                for (point in allPoints) {
                    val marker = Offset(
                        x = center.x + point.col * step,
                        y = center.y - point.row * step,
                    )
                    val zoneColor = when (zoneByPosition[point]) {
                        BoardZone.BORDER -> Color(0xFF2E2E32)
                        BoardZone.GATE -> Color(0xFF6C9D7A)
                        BoardZone.RED_GARDEN -> Color(0xFFD86A63)
                        BoardZone.WHITE_GARDEN -> Color(0xFFF2F0EC)
                        BoardZone.NEUTRAL_GARDEN, null -> Color.Transparent
                    }
                    if (zoneColor != Color.Transparent) {
                        drawCircle(color = zoneColor, radius = cellRadius, center = marker, style = Fill)
                    }
                }
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
                    val point = Offset(
                        x = center.x + position.col * step,
                        y = center.y - position.row * step,
                    )
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
                    val point = Offset(
                        x = center.x + position.col * step,
                        y = center.y - position.row * step,
                    )
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
                val marker = Offset(
                    x = center.x + point.col * step,
                    y = center.y - point.row * step,
                )
                drawCircle(color = Color(0xFF1F1A1B), radius = markerRadius, center = marker)
            }
            drawCircle(color = Color(0xFF2D2527), radius = radius * 0.998f, center = center, style = Stroke(width = 4f))

            // Draw selection/interaction highlights and piece tokens from the same pixel geometry.
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = pieceRadiusPx * 0.95f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            for (position in allPoints) {
                val token = cells[position].orEmpty()
                val isSource = selectedSource == position
                val isTarget = selectedTarget == position
                val isLegalTarget = position in legalTargets
                val isInteractiveHint = position in interactivePoints
                if (token.isEmpty() && !isSource && !isTarget && !isLegalTarget && !isInteractiveHint) continue

                val p = Offset(
                    x = center.x + position.col * step,
                    y = center.y - position.row * step,
                )
                val highlightColor = when {
                    isSource -> sourceHighlightColor
                    isTarget -> targetHighlightColor
                    isLegalTarget -> Color(0x8042A85A)
                    isInteractiveHint -> Color(0x22000000)
                    else -> Color.Transparent
                }
                if (highlightColor != Color.Transparent) {
                    drawCircle(color = highlightColor, radius = anchorRadiusPx, center = p, style = Fill)
                }

                val pieceCode = tokenCodeFromSnapshot(token)
                if (pieceCode != null) {
                    val isAiPiece = token.startsWith("A")
                    val pieceFill = if (isAiPiece) Color(0xFFB74B4B) else Color(0xFF3B66A6)
                    val pieceStroke = if (isAiPiece) Color(0xFF4A0D0F) else Color(0xFF0F2342)
                    drawCircle(color = pieceFill, radius = pieceRadiusPx, center = p, style = Fill)
                    drawCircle(color = pieceStroke, radius = pieceRadiusPx, center = p, style = Stroke(width = 2f))
                    drawIntoCanvas { canvas ->
                        val baseline = p.y - (textPaint.ascent() + textPaint.descent()) / 2f
                        canvas.nativeCanvas.drawText(pieceCode, p.x, baseline, textPaint)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReserveTray(
    state: GameUiState,
    onFlowerClick: (TileType) -> Unit,
    onAccentClick: (AccentType) -> Unit
) {
    val flowerOrder = listOf(
        TileType.ROSE, TileType.CHRYSANTHEMUM, TileType.RHODODENDRON,
        TileType.JASMINE, TileType.LILY, TileType.WHITE_JADE,
        TileType.WHITE_LOTUS, TileType.ORCHID
    )
    val accentOrder = listOf(AccentType.BOAT, AccentType.KNOTWEED, AccentType.WHEEL, AccentType.ROCK)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Flower tiles", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            flowerOrder.forEach { tile ->
                val count = state.flowerReserveCounts[tile] ?: 0
                val bonusEnabled = !state.isHarmonyBonusFlow || tile in state.harmonyBonusFlowerOptions
                ReserveToken(
                    code = tileCode(tile),
                    count = count,
                    selected = state.selectedTileType == tile,
                    enabled = if (state.isHarmonyBonusFlow) bonusEnabled && count > 0 else state.canInteract && count > 0,
                    onClick = { onFlowerClick(tile) }
                )
            }
        }

        Text("Accent tiles", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            accentOrder.forEach { accent ->
                val count = state.accentReserveCounts[accent] ?: 0
                val bonusEnabled = !state.isHarmonyBonusFlow || accent in state.harmonyBonusAccentOptions
                ReserveToken(
                    code = accentCode(accent),
                    count = count,
                    selected = state.selectedAccentType == accent,
                    enabled = if (state.isHarmonyBonusFlow) bonusEnabled && count > 0 else state.canInteract && count > 0,
                    onClick = { onAccentClick(accent) }
                )
            }
        }
    }
}

@Composable
private fun ReserveToken(
    code: String,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val fill = when {
        !enabled -> Color(0xFF7D7D7D)
        selected -> Color(0xFF2E7D32)
        else -> Color(0xFF455A64)
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(fill, CircleShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFFEAF7EB) else Color(0xFFDFE3E5),
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$code\n$count",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun tileCode(tile: TileType): String = when (tile) {
    TileType.ROSE -> "R3"
    TileType.CHRYSANTHEMUM -> "R4"
    TileType.RHODODENDRON -> "R5"
    TileType.JASMINE -> "W3"
    TileType.LILY -> "W4"
    TileType.WHITE_JADE -> "W5"
    TileType.WHITE_LOTUS -> "WL"
    TileType.ORCHID -> "OR"
}

private fun accentCode(accent: AccentType): String = when (accent) {
    AccentType.BOAT -> "BT"
    AccentType.KNOTWEED -> "KW"
    AccentType.WHEEL -> "WH"
    AccentType.ROCK -> "ST"
}

private fun tokenCodeFromSnapshot(token: String): String? {
    if (token.length < 3) return null
    return when (token.drop(1)) {
        "R3" -> "R3"
        "R4" -> "R4"
        "R5" -> "R5"
        "W3" -> "W3"
        "W4" -> "W4"
        "W5" -> "W5"
        "WL" -> "WL"
        "OR" -> "OR"
        "BT" -> "BT"
        "KN" -> "KW"
        "WH" -> "WH"
        "RK" -> "ST"
        else -> null
    }
}

