package com.paisho.app.ui

import androidx.lifecycle.ViewModel
import com.paisho.app.network.MultiplayerRepository
import com.paisho.core.ai.SimpleAi
import com.paisho.core.game.AccentType
import com.paisho.core.game.BonusAction
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GamePhase
import com.paisho.core.game.GameState
import com.paisho.core.game.Move
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.Rules
import com.paisho.core.game.RulesConfig
import com.paisho.core.game.TileType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    private data class HarmonyUndoState(
        val startState: GameState,
        val candidates: List<Move.Slide>,
    )

    private val ai = SimpleAi()
    private val multiplayerRepository = MultiplayerRepository()
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var state: GameState = GameState.initial(defaultRulesConfig())
    private var turnStartState: GameState = state
    private var hasPendingTurnChanges: Boolean = false
    private var stagedActions: List<String> = emptyList()
    private val persistedGames = mutableMapOf<String, PersistedGame>()
    private var currentGameId: String? = null
    private var pendingHarmonySlideCandidates: List<Move.Slide> = emptyList()
    private var pendingHarmonyStartState: GameState? = null
    private var pendingHarmonyPreviewState: GameState? = null
    private var stagedHarmonyUndoState: HarmonyUndoState? = null
    private val _uiState = MutableStateFlow(
        state.toUiState(
            log = listOf("Skud Pai Sho v0.0.18 - full rules engine enabled."),
            selectedTileType = null,
            selectedAccentType = null,
            isAwaitingSubmit = false,
            selectedSource = null,
            selectedTarget = null,
            legalTargets = emptySet(),
            stagedActions = emptyList(),
            isHarmonyBonusFlow = false,
            harmonyBonusFlowerOptions = emptySet(),
            harmonyBonusAccentOptions = emptySet(),
            canChooseNoBonus = false,
            projectedBoardSnapshot = emptyMap(),
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

    fun updateMultiplayerJoinGameIdInput(gameId: String) {
        _uiState.update {
            it.copy(
                multiplayerSession = it.multiplayerSession.copy(joinGameIdInput = gameId)
            )
        }
    }

    fun resumeGame(gameId: String) {
        val persisted = persistedGames[gameId] ?: return
        state = persisted.state
        currentGameId = gameId
        turnStartState = state
        hasPendingTurnChanges = false
        stagedActions = emptyList()
        clearHarmonyBonusState()
        clearStagedHarmonyUndoState()
        _uiState.value = state.toUiState(
            log = _uiState.value.eventLog + "Resumed ${persisted.title}.",
            selectedTileType = null,
            selectedAccentType = null,
            isAwaitingSubmit = false,
            selectedSource = null,
            selectedTarget = null,
            legalTargets = emptySet(),
            stagedActions = emptyList(),
            isHarmonyBonusFlow = false,
            harmonyBonusFlowerOptions = emptySet(),
            harmonyBonusAccentOptions = emptySet(),
            canChooseNoBonus = false,
            projectedBoardSnapshot = emptyMap(),
            setupState = _uiState.value.setupState,
            existingGames = _uiState.value.existingGames,
            appScreen = AppScreen.Game,
            drawerSection = DrawerSection.Game,
        )
    }

    fun openSettings() {
        _uiState.update { it.copy(appScreen = AppScreen.Settings, drawerSection = DrawerSection.Settings) }
    }

    fun openMultiplayer() {
        _uiState.update { it.copy(appScreen = AppScreen.Multiplayer, drawerSection = DrawerSection.Multiplayer) }
    }

    fun configureMultiplayer(baseUrl: String, playerId: String, playerName: String) {
        if (baseUrl.isBlank() || playerId.isBlank()) {
            appendLog("Multiplayer config requires base URL and player ID.")
            return
        }
        multiplayerRepository.configure(baseUrl = baseUrl, playerId = playerId)
        _uiState.update {
            it.copy(
                multiplayerSession = it.multiplayerSession.copy(
                    configured = true,
                    baseUrl = baseUrl,
                    playerId = playerId,
                    playerName = playerName.ifBlank { null },
                    lastError = null,
                ),
            )
        }
        appendLog("Multiplayer configured for player $playerId.")
    }

    fun loginMultiplayer() {
        val session = _uiState.value.multiplayerSession
        if (!session.configured) {
            appendLog("Configure multiplayer before login.")
            return
        }
        _uiState.update { it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = true, lastError = null)) }
        ioScope.launch {
            val result = multiplayerRepository.login()
            result.onSuccess { login ->
                _uiState.update {
                    it.copy(
                        multiplayerSession = it.multiplayerSession.copy(
                            isBusy = false,
                            token = login.token,
                            playerId = login.playerId,
                            lastError = null,
                        ),
                    )
                }
                appendLog("Multiplayer login successful for ${login.playerId}.")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = false, lastError = error.message))
                }
                appendLog("Multiplayer login failed: ${error.message}")
            }
        }
    }

    fun createOnlineGame() {
        val session = _uiState.value.multiplayerSession
        if (!session.configured) {
            appendLog("Configure multiplayer before creating an online game.")
            return
        }
        if (session.token.isNullOrBlank()) {
            appendLog("Login to multiplayer before creating an online game.")
            return
        }
        val accents = _uiState.value.setupState.selectedAccents.takeIf { it.size == 4 }
            ?: listOf(AccentType.ROCK, AccentType.WHEEL, AccentType.KNOTWEED, AccentType.BOAT)
        _uiState.update { it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = true, lastError = null)) }
        ioScope.launch {
            val result = multiplayerRepository.createGame(
                openingBasicType = _uiState.value.setupState.openingBasicType,
                hostAccentLoadout = accents,
                guestAccentLoadout = accents,
            )
            result.onSuccess { created ->
                _uiState.update {
                    it.copy(
                        multiplayerSession = it.multiplayerSession.copy(
                            isBusy = false,
                            gameId = created.summary.gameId,
                            serverVersion = created.summary.version,
                            lastError = null,
                        ),
                    )
                }
                appendLog("Created online game ${created.summary.gameId.take(8)}.")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = false, lastError = error.message))
                }
                appendLog("Create online game failed: ${error.message}")
            }
        }
    }

    fun refreshOnlineGame() {
        val gameId = _uiState.value.multiplayerSession.gameId
        if (gameId.isNullOrBlank()) {
            appendLog("No online game selected to refresh.")
            return
        }
        _uiState.update { it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = true, lastError = null)) }
        ioScope.launch {
            val result = multiplayerRepository.getGame(gameId)
            result.onSuccess { details ->
                _uiState.update {
                    it.copy(
                        multiplayerSession = it.multiplayerSession.copy(
                            isBusy = false,
                            gameId = details.summary.gameId,
                            serverVersion = details.summary.version,
                            joinGameIdInput = "",
                            lastError = null,
                        ),
                    )
                }
                appendLog("Refreshed online game ${details.summary.gameId.take(8)}.")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = false, lastError = error.message))
                }
                appendLog("Refresh online game failed: ${error.message}")
            }
        }
    }

    fun joinOnlineGame(gameId: String) {
        if (gameId.isBlank()) {
            appendLog("Game ID is required to join an online game.")
            return
        }
        val session = _uiState.value.multiplayerSession
        if (session.token.isNullOrBlank()) {
            appendLog("Login to multiplayer before joining an online game.")
            return
        }
        _uiState.update { it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = true, lastError = null)) }
        ioScope.launch {
            val result = multiplayerRepository.joinGame(gameId.trim())
            result.onSuccess { details ->
                _uiState.update {
                    it.copy(
                        multiplayerSession = it.multiplayerSession.copy(
                            isBusy = false,
                            gameId = details.summary.gameId,
                            serverVersion = details.summary.version,
                            lastError = null,
                        ),
                    )
                }
                appendLog("Joined online game ${details.summary.gameId.take(8)}.")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = false, lastError = error.message))
                }
                appendLog("Join online game failed: ${error.message}")
            }
        }
    }

    fun listOnlineGames() {
        val session = _uiState.value.multiplayerSession
        if (session.token.isNullOrBlank()) {
            appendLog("Login to multiplayer before listing online games.")
            return
        }
        _uiState.update { it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = true, lastError = null)) }
        ioScope.launch {
            val result = multiplayerRepository.listGames()
            result.onSuccess { games ->
                _uiState.update {
                    it.copy(
                        multiplayerSession = it.multiplayerSession.copy(
                            isBusy = false,
                            games = games.map { summary ->
                                MultiplayerGameSummary(
                                    gameId = summary.gameId,
                                    title = summary.title,
                                    status = summary.status.name,
                                    turnNumber = summary.turnNumber,
                                    currentTurnPlayerId = summary.currentTurnPlayerId,
                                )
                            },
                            lastError = null,
                        ),
                    )
                }
                appendLog("Loaded ${games.size} online game(s).")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(multiplayerSession = it.multiplayerSession.copy(isBusy = false, lastError = error.message))
                }
                appendLog("List online games failed: ${error.message}")
            }
        }
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
        clearHarmonyBonusState()
        clearStagedHarmonyUndoState()
        turnStartState = state
        hasPendingTurnChanges = false
        stagedActions = emptyList()
        val updatedExistingGames = addExistingGameRecord(_uiState.value.existingGames, setup)
        currentGameId = updatedExistingGames.firstOrNull()?.id
        _uiState.value = state.toUiState(
            log = listOf(
                "New game started.",
                "Opening tile: ${setup.openingBasicType.name}.",
                "Selected accents: ${setup.selectedAccents.joinToString { it.name }}."
            ),
            selectedTileType = null,
            selectedAccentType = null,
            isAwaitingSubmit = false,
            selectedSource = null,
            selectedTarget = null,
            legalTargets = emptySet(),
            stagedActions = emptyList(),
            isHarmonyBonusFlow = false,
            harmonyBonusFlowerOptions = emptySet(),
            harmonyBonusAccentOptions = emptySet(),
            canChooseNoBonus = false,
            projectedBoardSnapshot = emptyMap(),
            setupState = setup,
            existingGames = updatedExistingGames,
            appScreen = AppScreen.Game,
            drawerSection = DrawerSection.Game,
        )
        syncPersistedGames(updatedExistingGames)
    }

    fun onPositionSelected(position: Position) {
        if (state.phase == GamePhase.FINISHED || state.currentPlayer != Player.HUMAN) return
        if (isHarmonyBonusPending()) {
            val ui = _uiState.value
            val matches = harmonyBonusMatches(
                tileType = ui.selectedTileType,
                accentType = ui.selectedAccentType,
            )
            val selectedMove = matches.firstOrNull { bonusTargetPosition(it) == position }
            if (selectedMove != null && position in ui.legalTargets) {
                tryApplyHumanMove(selectedMove)
            } else {
                appendLog("Harmony formed. Select a reserve tile for the bonus, then pick a highlighted target.")
            }
            return
        }
        val currentUi = _uiState.value
        if (!currentUi.canInteract) return
        val tappedFlower = state.flowerAt(position)
        val source = currentUi.selectedSource
        if (source != null) {
            when {
                position == source -> clearSelection()
                position in currentUi.legalTargets -> tryApplySelectedSlide(source = source, target = position)
                tappedFlower?.owner == Player.HUMAN -> selectSourceFlower(position)
                else -> _uiState.update { it.copy(selectedTarget = null) }
            }
            return
        }

        if (tappedFlower?.owner == Player.HUMAN) {
            selectSourceFlower(position)
            return
        }

        val selectedTileType = currentUi.selectedTileType
        if (selectedTileType != null && position in currentUi.legalTargets) {
            tryStagePlant(selectedTileType, position)
            return
        }
    }

    fun selectFlowerReserveTile(tileType: TileType) {
        if (isHarmonyBonusPending()) {
            val matches = harmonyBonusMatches(tileType = tileType, accentType = null)
            if (matches.isEmpty()) {
                appendLog("That flower tile is not legal for this Harmony bonus.")
                return
            }
            val legalTargets = matches.mapNotNull { bonusTargetPosition(it) }.toSet()
            _uiState.update {
                it.copy(
                    selectedTileType = tileType,
                    selectedAccentType = null,
                    selectedSource = null,
                    selectedTarget = null,
                    legalTargets = legalTargets,
                )
            }
            return
        }
        if (state.currentPlayer != Player.HUMAN || _uiState.value.isAwaitingSubmit) return
        val reserve = state.reserveFor(Player.HUMAN)
        val count = if (tileType.isBasic) reserve.basicCount(tileType) else reserve.specialCount(tileType)
        if (count <= 0) return
        val legalTargets = legalPlantTargets(tileType)
        _uiState.update {
            it.copy(
                selectedTileType = tileType,
                selectedAccentType = null,
                selectedSource = null,
                selectedTarget = null,
                legalTargets = legalTargets,
            )
        }
    }

    fun selectAccentReserveTile(accentType: AccentType) {
        if (isHarmonyBonusPending()) {
            val matches = harmonyBonusMatches(tileType = null, accentType = accentType)
            if (matches.isEmpty()) {
                appendLog("That accent tile is not legal for this Harmony bonus.")
                return
            }
            val legalTargets = matches.mapNotNull { bonusTargetPosition(it) }.toSet()
            _uiState.update {
                it.copy(
                    selectedAccentType = accentType,
                    selectedTileType = null,
                    selectedSource = null,
                    selectedTarget = null,
                    legalTargets = legalTargets,
                )
            }
            return
        }
        if (state.currentPlayer != Player.HUMAN || _uiState.value.isAwaitingSubmit) return
        val count = state.reserveFor(Player.HUMAN).accentCount(accentType)
        if (count <= 0) return
        _uiState.update {
            it.copy(
                selectedAccentType = accentType,
                selectedTileType = null,
                selectedSource = null,
                selectedTarget = null,
                legalTargets = emptySet(),
            )
        }
        appendLog("Accent ${accentCode(accentType)} selected. Form a new Harmony and choose a bonus action.")
    }

    fun chooseHarmonyNoBonus() {
        if (!isHarmonyBonusPending()) return
        val preview = pendingHarmonyPreviewState ?: return
        val start = pendingHarmonyStartState ?: return
        if (!hasPendingTurnChanges) turnStartState = start
        state = preview
        stagedActions = stagedActions + "No bonus chosen"
        appendLog("Harmony bonus skipped.")
        clearHarmonyBonusState()
        hasPendingTurnChanges = true
        publishState(clearSelection = true)
    }

    fun submitTurn() {
        if (!hasPendingTurnChanges) return
        if (state.phase != GamePhase.FINISHED && state.winner == null && !state.isDraw && state.currentPlayer == Player.AI) {
            val aiMove = ai.chooseMove(state)
            if (aiMove != null) {
                state = Rules.applyMove(state, aiMove)
                appendLog("AI played: $aiMove")
            } else {
                appendLog("AI has no legal moves. Passing turn.")
                state = state.copy(
                    currentPlayer = Player.HUMAN,
                    turnNumber = state.turnNumber + 1,
                )
            }
        }
        hasPendingTurnChanges = false
        turnStartState = state
        stagedActions = emptyList()
        clearHarmonyBonusState()
        clearStagedHarmonyUndoState()
        syncPersistedGames(_uiState.value.existingGames)
        publishState(clearSelection = true)
    }

    fun undoTurn() {
        if (isHarmonyBonusPending()) {
            state = pendingHarmonyStartState ?: state
            turnStartState = state
            hasPendingTurnChanges = false
            stagedActions = emptyList()
            clearHarmonyBonusState()
            clearStagedHarmonyUndoState()
            appendLog("Harmony bonus selection cleared.")
            syncPersistedGames(_uiState.value.existingGames)
            publishState(clearSelection = true)
            return
        }
        val harmonyUndo = stagedHarmonyUndoState
        if (harmonyUndo != null) {
            state = harmonyUndo.startState
            turnStartState = state
            hasPendingTurnChanges = false
            stagedActions = emptyList()
            pendingHarmonySlideCandidates = harmonyUndo.candidates
            pendingHarmonyStartState = harmonyUndo.startState
            clearStagedHarmonyUndoState()
            appendLog("Returned to Harmony bonus selection.")
            syncPersistedGames(_uiState.value.existingGames)
            publishState(clearSelection = true)
            return
        }
        if (!hasPendingTurnChanges) return
        state = turnStartState
        hasPendingTurnChanges = false
        stagedActions = emptyList()
        clearHarmonyBonusState()
        clearStagedHarmonyUndoState()
        appendLog("Undid staged turn changes.")
        syncPersistedGames(_uiState.value.existingGames)
        publishState(clearSelection = true)
    }

    private fun tryApplySelectedSlide(source: Position, target: Position) {
        if (state.winner != null || state.isDraw || state.currentPlayer != Player.HUMAN) return
        val legalMoves = Rules.legalMoves(state)
        val tileId = state.flowerAt(source)?.id ?: return
        val candidates = legalMoves.filterIsInstance<Move.Slide>()
            .filter { it.tileId == tileId && it.target == target }
        if (candidates.isEmpty()) {
            appendLog("No legal arrange move from source to target.")
            return
        }
        val bonusCandidates = candidates.filter { it.bonus != null }.distinctBy { it.bonus }
        if (bonusCandidates.isEmpty()) {
            tryApplyHumanMove(candidates.first())
            return
        }
        pendingHarmonySlideCandidates = bonusCandidates
        pendingHarmonyStartState = state
        pendingHarmonyPreviewState = Rules.applyMove(state, candidates.first())
        clearStagedHarmonyUndoState()
        appendLog("Harmony formed. Select a reserve tile to choose your bonus.")
        publishState(clearSelection = true)
        _uiState.update {
            it.copy(
                selectedSource = source,
                selectedTarget = target,
                legalTargets = emptySet(),
                selectedTileType = null,
                selectedAccentType = null,
            )
        }
    }

    private fun tryStagePlant(tileType: TileType, gate: Position) {
        if (state.winner != null || state.isDraw || state.currentPlayer != Player.HUMAN) return
        val plant = Move.Plant(tileType, gate)
        val legalMoves = Rules.legalMoves(state)
        if (plant !in legalMoves) {
            appendLog("Plant is not legal at that gate.")
            return
        }
        tryApplyHumanMove(plant)
    }

    private fun tryApplyHumanMove(move: Move) {
        val harmonyUndoState = if (isHarmonyBonusPending()) {
            val start = pendingHarmonyStartState
            if (start != null && pendingHarmonySlideCandidates.isNotEmpty()) {
                HarmonyUndoState(startState = start, candidates = pendingHarmonySlideCandidates)
            } else {
                null
            }
        } else {
            null
        }
        val legal = Rules.legalMoves(state)
        if (move !in legal) {
            appendLog("Illegal move rejected: $move")
            return
        }

        if (!hasPendingTurnChanges) turnStartState = state
        state = Rules.applyMove(state, move)
        val stagedLabel = move.toStagedActionLabel(turnStartState)
        stagedActions = stagedActions + stagedLabel
        appendLog("Staged move: $stagedLabel")
        clearHarmonyBonusState()
        stagedHarmonyUndoState = harmonyUndoState
        hasPendingTurnChanges = true
        publishState(clearSelection = true)
    }

    private fun publishState(clearSelection: Boolean) {
        val priorLog = _uiState.value.eventLog
        val source = if (clearSelection) null else _uiState.value.selectedSource
        val target = if (clearSelection) null else _uiState.value.selectedTarget
        val legalTargets = if (clearSelection) emptySet() else _uiState.value.legalTargets
        val selectedTileType = if (clearSelection) null else _uiState.value.selectedTileType
        val selectedAccentType = if (clearSelection) null else _uiState.value.selectedAccentType
        val currentExisting = _uiState.value.existingGames
        syncPersistedGames(currentExisting)
        _uiState.value = state.toUiState(
            log = priorLog,
            selectedTileType = selectedTileType,
            selectedAccentType = selectedAccentType,
            isAwaitingSubmit = hasPendingTurnChanges,
            selectedSource = source,
            selectedTarget = target,
            legalTargets = legalTargets,
            stagedActions = stagedActions,
            isHarmonyBonusFlow = isHarmonyBonusPending(),
            harmonyBonusFlowerOptions = harmonyBonusFlowerOptions(),
            harmonyBonusAccentOptions = harmonyBonusAccentOptions(),
            canChooseNoBonus = isHarmonyBonusPending(),
            projectedBoardSnapshot = pendingHarmonyPreviewState?.boardSnapshot() ?: emptyMap(),
            setupState = _uiState.value.setupState,
            existingGames = currentExisting,
            appScreen = _uiState.value.appScreen,
            drawerSection = _uiState.value.drawerSection,
        )
    }

    private fun appendLog(entry: String) {
        _uiState.update { it.copy(eventLog = (it.eventLog + entry).takeLast(40)) }
    }

    private fun GameState.toUiState(
        log: List<String>,
        selectedTileType: TileType?,
        selectedAccentType: AccentType?,
        isAwaitingSubmit: Boolean,
        selectedSource: Position?,
        selectedTarget: Position?,
        legalTargets: Set<Position>,
        stagedActions: List<String>,
        isHarmonyBonusFlow: Boolean,
        harmonyBonusFlowerOptions: Set<TileType>,
        harmonyBonusAccentOptions: Set<AccentType>,
        canChooseNoBonus: Boolean,
        projectedBoardSnapshot: Map<Position, String>,
        setupState: NewGameSetupState,
        existingGames: List<ExistingGameSummary>,
        appScreen: AppScreen,
        drawerSection: DrawerSection,
    ): GameUiState {
        val reserveOwner = if (isAwaitingSubmit) Player.HUMAN else currentPlayer
        val reserve = reserves[reserveOwner] ?: reserves.getValue(Player.HUMAN)
        val flowerCounts = (TileType.basicTypes + TileType.specialTypes).associateWith { tile ->
            if (tile.isBasic) reserve.basicCount(tile) else reserve.specialCount(tile)
        }
        val accentCounts = AccentType.entries.associateWith { accent -> reserve.accentCount(accent) }

        return GameUiState(
        boardSize = rules.boardSize,
        coordinateExtent = rules.coordinateExtent,
        currentPlayer = if (isAwaitingSubmit) Player.HUMAN else currentPlayer,
        isAwaitingSubmit = isAwaitingSubmit,
        canSubmitTurn = isAwaitingSubmit,
        canUndoTurn = isAwaitingSubmit || isHarmonyBonusFlow,
        canInteract = !isAwaitingSubmit && winner == null && !isDraw && phase != GamePhase.FINISHED && currentPlayer == Player.HUMAN,
        selectedSource = selectedSource,
        selectedTarget = selectedTarget,
        legalTargets = legalTargets,
        stagedActions = stagedActions,
        isHarmonyBonusFlow = isHarmonyBonusFlow,
        harmonyBonusFlowerOptions = harmonyBonusFlowerOptions,
        harmonyBonusAccentOptions = harmonyBonusAccentOptions,
        canChooseNoBonus = canChooseNoBonus,
        projectedBoardSnapshot = projectedBoardSnapshot,
        legalPositions = rules.legalPositions,
        zoneByPosition = rules.zoneByPosition,
        boardVisualConfig = defaultBoardVisualConfig(),
        selectedTileType = selectedTileType,
        selectedAccentType = selectedAccentType,
        flowerReserveCounts = flowerCounts,
        accentReserveCounts = accentCounts,
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
    }

    private fun selectSourceFlower(position: Position) {
        if (isHarmonyBonusPending()) {
            appendLog("Harmony formed. Select a reserve tile to choose your bonus.")
            return
        }
        val legalTargets = Rules.legalMovesFrom(state, position).map { it.target }.toSet()
        _uiState.update {
            it.copy(
                selectedSource = position,
                selectedTarget = null,
                selectedTileType = null,
                selectedAccentType = null,
                legalTargets = legalTargets,
            )
        }
    }

    private fun isHarmonyBonusPending(): Boolean = pendingHarmonySlideCandidates.isNotEmpty()

    private fun harmonyBonusMatches(
        tileType: TileType?,
        accentType: AccentType?,
    ): List<Move.Slide> {
        return pendingHarmonySlideCandidates.filter { candidate ->
            when (val bonus = candidate.bonus) {
                is BonusAction.PlantBonus -> tileType != null && bonus.tileType == tileType
                is BonusAction.PlaceAccent -> accentType != null && bonus.type == accentType
                is BonusAction.BoatMove,
                is BonusAction.BoatRemoveAccent -> accentType == AccentType.BOAT
                null -> false
            }
        }
    }

    private fun bonusTargetPosition(move: Move.Slide): Position? = when (val bonus = move.bonus) {
        is BonusAction.PlaceAccent -> bonus.target
        is BonusAction.PlantBonus -> bonus.gate
        is BonusAction.BoatMove -> bonus.destination
        is BonusAction.BoatRemoveAccent -> bonus.targetAccent
        null -> null
    }

    private fun harmonyBonusFlowerOptions(): Set<TileType> =
        pendingHarmonySlideCandidates.mapNotNull { (it.bonus as? BonusAction.PlantBonus)?.tileType }.toSet()

    private fun harmonyBonusAccentOptions(): Set<AccentType> =
        pendingHarmonySlideCandidates.mapNotNull { move ->
            when (val bonus = move.bonus) {
                is BonusAction.PlaceAccent -> bonus.type
                is BonusAction.BoatMove, is BonusAction.BoatRemoveAccent -> AccentType.BOAT
                else -> null
            }
        }.toSet()

    private fun legalPlantTargets(tileType: TileType): Set<Position> {
        return Rules.legalMoves(state)
            .filterIsInstance<Move.Plant>()
            .filter { it.type == tileType }
            .map { it.target }
            .toSet()
    }

    private fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedSource = null,
                selectedTarget = null,
                selectedTileType = null,
                selectedAccentType = null,
                legalTargets = emptySet(),
            )
        }
    }

    private fun Move.toStagedActionLabel(beforeState: GameState): String = when (this) {
        is Move.Plant -> "Plant ${tileCode(type)} at (${target.row}, ${target.col})"
        is Move.Slide -> {
            val from = beforeState.flowers.firstOrNull { it.id == tileId }?.position
            val moveBonus = bonus
            val base = if (from != null) {
                "Move (${from.row}, ${from.col}) -> (${target.row}, ${target.col})"
            } else {
                "Move tile #$tileId -> (${target.row}, ${target.col})"
            }
            if (moveBonus != null) "$base + ${bonusLabel(moveBonus)}" else base
        }
    }

    private fun bonusLabel(bonus: BonusAction): String = when (bonus) {
        is BonusAction.PlaceAccent -> "Accent ${accentCode(bonus.type)} at (${bonus.target.row}, ${bonus.target.col})"
        is BonusAction.PlantBonus -> "Bonus plant ${tileCode(bonus.tileType)} at (${bonus.gate.row}, ${bonus.gate.col})"
        is BonusAction.BoatMove -> "Boat move (${bonus.source.row}, ${bonus.source.col}) -> (${bonus.destination.row}, ${bonus.destination.col})"
        is BonusAction.BoatRemoveAccent -> "Boat remove accent at (${bonus.targetAccent.row}, ${bonus.targetAccent.col})"
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

    private fun clearHarmonyBonusState() {
        pendingHarmonySlideCandidates = emptyList()
        pendingHarmonyStartState = null
        pendingHarmonyPreviewState = null
    }

    private fun clearStagedHarmonyUndoState() {
        stagedHarmonyUndoState = null
    }

    private fun defaultRulesConfig(): RulesConfig = RulesConfig(
        openingBasicType = TileType.ROSE,
        humanStartGate = Position(-8, 0),
        aiStartGate = Position(8, 0),
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

    private fun syncPersistedGames(existing: List<ExistingGameSummary>) {
        existing.forEach { summary ->
            val old = persistedGames[summary.id]
            val isCurrent = summary.id == currentGameId
            if (old == null) {
                persistedGames[summary.id] = PersistedGame(
                    id = summary.id,
                    title = summary.title,
                    subtitle = summary.subtitle,
                    state = state,
                )
                return@forEach
            }
            persistedGames[summary.id] = old.copy(
                title = summary.title,
                subtitle = summary.subtitle,
                state = if (isCurrent) state else old.state,
            )
        }
        val validIds = existing.map { it.id }.toSet()
        persistedGames.keys.retainAll(validIds)
    }

    private fun defaultBoardVisualConfig(): BoardVisualConfig = BoardVisualConfig(
        backgroundImageResId = null,
        backgroundScale = 1f,
        backgroundOffsetXFraction = 0f,
        backgroundOffsetYFraction = 0f,
        showZoneMarkers = true,
    )
}

