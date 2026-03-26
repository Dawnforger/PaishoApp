package com.paisho.app.ui

import androidx.lifecycle.ViewModel
import com.paisho.core.ai.SimpleAi
import com.paisho.core.game.GamePhase
import com.paisho.core.game.BonusAction
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GameState
import com.paisho.core.game.Move
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.Rules
import com.paisho.core.game.TileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GameUiState(
    val boardSize: Int = 9,
    val currentPlayer: Player = Player.HUMAN,
    val selectedSource: Position? = null,
    val selectedTarget: Position? = null,
    val legalTargets: Set<Position> = emptySet(),
    val selectedTileType: TileType? = TileType.CHRYSANTHEMUM,
    val boardSnapshot: Map<Position, String> = emptyMap(),
    val eventLog: List<String> = listOf("Welcome to Pai Sho MVP."),
    val isGameOver: Boolean = false,
    val isDraw: Boolean = false,
    val winner: Player? = null,
    val endReason: GameEndReason? = null,
    val phase: GamePhase = GamePhase.PLAYING,
)

class GameViewModel : ViewModel() {
    private val ai = SimpleAi()
    private var state: GameState = GameState.initial()
    private var pendingBonus: BonusAction? = null
    private val _uiState = MutableStateFlow(
        state.toUiState(
            log = listOf("Skud Pai Sho v0.0.01 - full rules engine enabled."),
            selectedTileType = TileType.CHRYSANTHEMUM,
            selectedSource = null,
            selectedTarget = null,
            legalTargets = emptySet(),
        )
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

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
        state = GameState.initial()
        pendingBonus = null
        _uiState.value = state.toUiState(
            log = listOf("Game reset."),
            selectedTileType = TileType.CHRYSANTHEMUM,
            selectedSource = null,
            selectedTarget = null,
            legalTargets = emptySet(),
        )
    }

    private fun publishState(clearSelection: Boolean) {
        val priorLog = _uiState.value.eventLog
        val source = if (clearSelection) null else _uiState.value.selectedSource
        val target = if (clearSelection) null else _uiState.value.selectedTarget
        val legalTargets = if (clearSelection) emptySet() else _uiState.value.legalTargets
        val selectedTileType = _uiState.value.selectedTileType ?: TileType.CHRYSANTHEMUM
        _uiState.value = state.toUiState(
            log = priorLog,
            selectedTileType = selectedTileType,
            selectedSource = source,
            selectedTarget = target,
            legalTargets = legalTargets,
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
    ): GameUiState = GameUiState(
        boardSize = rules.boardSize,
        currentPlayer = currentPlayer,
        selectedSource = selectedSource,
        selectedTarget = selectedTarget,
        legalTargets = legalTargets,
        selectedTileType = selectedTileType,
        boardSnapshot = boardSnapshot(),
        eventLog = log,
        isGameOver = winner != null || isDraw || phase == GamePhase.FINISHED,
        isDraw = isDraw,
        winner = winner,
        endReason = endReason,
        phase = phase,
    )
}
