package com.paisho.app.ui

import androidx.lifecycle.ViewModel
import com.paisho.core.ai.SimpleAi
import com.paisho.core.game.AccentType
import com.paisho.core.game.GamePhase
import com.paisho.core.game.BonusAction
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GameState
import com.paisho.core.game.Move
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.RulesConfig
import com.paisho.core.game.Rules
import com.paisho.core.game.TileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class GameViewModel : ViewModel() {
    private val ai = SimpleAi()
    private var state: GameState = GameState.initial(defaultRulesConfig())
    private var pendingBonus: BonusAction? = null
    private val _uiState = MutableStateFlow(
        state.toUiState(
            log = listOf("Skud Pai Sho v0.0.03 - full rules engine enabled."),
            selectedTileType = TileType.ROSE,
            selectedSource = null,
            selectedTarget = null,
            legalTargets = emptySet(),
            setupState = NewGameSetupState(),
            existingGames = emptyList(),
            appScreen = AppScreen.Home,
            drawerSection = DrawerSection.Game,
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun openHome() {
        _uiState.update { it.copy(appScreen = AppScreen.Home, drawerSection = DrawerSection.Game) }
    }

    fun openExistingGames() {
        _uiState.update { it.copy(appScreen = AppScreen.ExistingGames, drawerSection = DrawerSection.ExistingGames) }
    }

    fun openSettings() {
        _uiState.update { it.copy(appScreen = AppScreen.Settings, drawerSection = DrawerSection.Settings) }
    }

    fun startNewGameFlow() {
        _uiState.update {
            it.copy(
                appScreen = AppScreen.NewGameSetup,
                drawerSection = DrawerSection.Game,
                setupState = NewGameSetupState()
            )
        }
    }

    fun toggleOpeningTile(type: TileType) {
        if (!type.isBasic) return
        _uiState.update { ui ->
            ui.copy(setupState = ui.setupState.copy(openingBasicType = type))
        }
    }

    fun toggleAccentSelection(type: AccentType) {
        _uiState.update { ui ->
            val selected = ui.setupState.selectedAccents.toMutableList()
            val currentCount = selected.count { it == type }
            when {
                currentCount >= 2 -> {
                    // Already at max for this accent type; ignore add.
                    return@update ui
                }
                selected.size < 4 -> selected.add(type)
                else -> return@update ui
            }
            ui.copy(setupState = ui.setupState.copy(selectedAccents = selected))
        }
    }

    fun removeAccentSelection(type: AccentType) {
        _uiState.update { ui ->
            val selected = ui.setupState.selectedAccents.toMutableList()
            val idx = selected.indexOf(type)
            if (idx >= 0) {
                selected.removeAt(idx)
                ui.copy(setupState = ui.setupState.copy(selectedAccents = selected))
            } else {
                ui
            }
        }
    }

    fun createNewGameFromSetup() {
        val setup = _uiState.value.setupState
        if (setup.selectedAccents.size != 4) {
            appendLog("Select exactly 4 accents to start.")
            return
        }
        if (AccentType.entries.any { type -> setup.selectedAccents.count { it == type } > 2 }) {
            appendLog("No accent type can be selected more than twice.")
            return
        }
        val config = defaultRulesConfig().copy(
            openingBasicType = setup.openingBasicType,
            humanAccentLoadout = setup.selectedAccents,
            aiAccentLoadout = setup.selectedAccents,
        )
        state = GameState.initial(config)
        pendingBonus = null
        _uiState.value = state.toUiState(
            log = listOf(
                "New game started.",
                "Opening tile: ${setup.openingBasicType.name}.",
                "Selected accents: ${setup.selectedAccents.joinToString { it.name }}."
            ),
            selectedTileType = setup.openingBasicType,
            selectedSource = null,
            selectedTarget = null,
            legalTargets = emptySet(),
            setupState = setup,
            existingGames = addExistingGameRecord(_uiState.value.existingGames, setup),
            appScreen = AppScreen.Game,
            drawerSection = DrawerSection.Game,
        )
    }

    fun onPositionSelected(position: Position) {
        if (state.phase == GamePhase.FINISHED) return
        val tappedFlower = state.flowerAt(position)
        if (tappedFlower?.owner == Player.HUMAN) {
            val legalTargets = Rules.legalMovesFrom(state, position).map { it.target }.toSet()
            _uiState.update {
                it.copy(
                    selectedSource = position,
                    selectedTarget = null,
                    legalTargets = legalTargets,
                )
            }
        } else {
            _uiState.update { it.copy(selectedTarget = position) }
        }
    }

    fun selectTileToPlant(tileType: TileType) {
        _uiState.update { it.copy(selectedTileType = tileType) }
    }

    fun performSelectedMoveOrPlant() {
        if (state.winner != null || state.isDraw || state.currentPlayer != Player.HUMAN) return
        val ui = _uiState.value
        val legalMoves = Rules.legalMoves(state)

        val move = if (ui.selectedSource != null) {
            val source = ui.selectedSource
            val target = ui.selectedTarget ?: return
            val tileId = state.flowerAt(source)?.id ?: return
            val candidates = legalMoves.filterIsInstance<Move.Slide>()
                .filter { it.tileId == tileId && it.target == target }
            if (candidates.isEmpty()) {
                appendLog("No legal arrange move from source to target.")
                return
            }
            val selected = when {
                candidates.any { it.bonus == pendingBonus } -> candidates.first { it.bonus == pendingBonus }
                candidates.any { it.bonus == null } -> candidates.first { it.bonus == null }
                else -> candidates.first()
            }
            pendingBonus = selected.bonus
            selected
        } else {
            val gate = ui.selectedTarget ?: return
            val type = ui.selectedTileType ?: TileType.CHRYSANTHEMUM
            val plant = Move.Plant(type, gate)
            if (plant !in legalMoves) {
                appendLog("Plant is not legal at that gate.")
                return
            }
            plant
        }

        tryApplyHumanMove(move)
    }

    private fun tryApplyHumanMove(move: Move) {
        val legal = Rules.legalMoves(state)
        if (move !in legal) {
            appendLog("Illegal move rejected: $move")
            return
        }

        state = Rules.applyMove(state, move)
        appendLog("Human played: $move")
        pendingBonus = null
        publishState(clearSelection = true)

        if (state.winner == null && !state.isDraw && state.currentPlayer == Player.AI) {
            val aiMove = ai.chooseMove(state)
            if (aiMove != null) {
                state = Rules.applyMove(state, aiMove)
                appendLog("AI played: $aiMove")
            } else {
                appendLog("AI has no legal moves.")
            }
            publishState(clearSelection = true)
        }
    }

    fun resetGame() {
        state = GameState.initial(defaultRulesConfig())
        pendingBonus = null
        _uiState.value = state.toUiState(
            log = listOf("Game reset."),
            selectedTileType = TileType.ROSE,
            selectedSource = null,
            selectedTarget = null,
            legalTargets = emptySet(),
            setupState = _uiState.value.setupState,
            existingGames = _uiState.value.existingGames,
            appScreen = AppScreen.Game,
            drawerSection = DrawerSection.Game,
        )
    }

    private fun publishState(clearSelection: Boolean) {
        val priorLog = _uiState.value.eventLog
        val source = if (clearSelection) null else _uiState.value.selectedSource
        val target = if (clearSelection) null else _uiState.value.selectedTarget
        val legalTargets = if (clearSelection) emptySet() else _uiState.value.legalTargets
        val selectedTileType = _uiState.value.selectedTileType ?: TileType.ROSE
        _uiState.value = state.toUiState(
            log = priorLog,
            selectedTileType = selectedTileType,
            selectedSource = source,
            selectedTarget = target,
            legalTargets = legalTargets,
            setupState = _uiState.value.setupState,
            existingGames = _uiState.value.existingGames,
            appScreen = _uiState.value.appScreen,
            drawerSection = _uiState.value.drawerSection,
        )
    }

    private fun appendLog(entry: String) {
        _uiState.update { it.copy(eventLog = (it.eventLog + entry).takeLast(40)) }
    }

    private fun GameState.toUiState(
        log: List<String>,
        selectedTileType: TileType,
        selectedSource: Position?,
        selectedTarget: Position?,
        legalTargets: Set<Position>,
        setupState: NewGameSetupState,
        existingGames: List<ExistingGameSummary>,
        appScreen: AppScreen,
        drawerSection: DrawerSection,
    ): GameUiState = GameUiState(
        boardSize = rules.boardSize,
        coordinateExtent = rules.coordinateExtent,
        currentPlayer = currentPlayer,
        selectedSource = selectedSource,
        selectedTarget = selectedTarget,
        legalTargets = legalTargets,
        legalPositions = rules.legalPositions,
        zoneByPosition = rules.zoneByPosition,
        selectedTileType = selectedTileType,
        boardSnapshot = boardSnapshot(),
        eventLog = log,
        isGameOver = winner != null || isDraw || phase == GamePhase.FINISHED,
        isDraw = isDraw,
        winner = winner,
        endReason = endReason,
        phase = phase,
        setupState = setupState,
        existingGames = existingGames,
        appScreen = appScreen,
        drawerSection = drawerSection,
    )

    private fun defaultRulesConfig(): RulesConfig = RulesConfig(
        openingBasicType = TileType.ROSE,
        humanAccentLoadout = listOf(AccentType.ROCK, AccentType.WHEEL, AccentType.KNOTWEED, AccentType.BOAT),
        aiAccentLoadout = listOf(AccentType.ROCK, AccentType.WHEEL, AccentType.KNOTWEED, AccentType.BOAT),
    )

    private fun addExistingGameRecord(
        existing: List<ExistingGameSummary>,
        setup: NewGameSetupState,
    ): List<ExistingGameSummary> {
        val next = ExistingGameSummary(
            id = "game-${System.currentTimeMillis()}",
            title = "Game ${existing.size + 1}",
            subtitle = "Opening ${setup.openingBasicType.shortName} | ${setup.selectedAccents.joinToString("/") { it.shortName }}",
        )
        return (listOf(next) + existing).take(10)
    }
}

