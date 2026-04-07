package com.paisho.app.ui

import com.paisho.core.game.AccentType
import com.paisho.core.game.BoardZone
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GamePhase
import com.paisho.core.game.GameState
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.TileType

enum class AppScreen {
    Home,
    NewGameSetup,
    Game,
    Multiplayer,
    ExistingGames,
    Settings,
}

enum class DrawerSection {
    Game,
    Multiplayer,
    ExistingGames,
    Settings,
}

data class ExistingGameSummary(
    val id: String,
    val title: String,
    val subtitle: String,
)

data class MultiplayerGameSummary(
    val gameId: String,
    val title: String,
    val status: String,
    val turnNumber: Int,
    val currentTurnPlayerId: String?,
)

data class MultiplayerSession(
    val configured: Boolean = false,
    val baseUrl: String? = null,
    val token: String? = null,
    val playerId: String? = null,
    val playerName: String? = null,
    val gameId: String? = null,
    val serverVersion: Int? = null,
    val joinGameIdInput: String = "",
    val isBusy: Boolean = false,
    val games: List<MultiplayerGameSummary> = emptyList(),
    val lastError: String? = null,
)

data class PersistedGame(
    val id: String,
    val title: String,
    val subtitle: String,
    val state: GameState,
)

data class BoardVisualConfig(
    val backgroundImageResId: Int? = null,
    val backgroundScale: Float = 1f,
    val backgroundOffsetXFraction: Float = 0f,
    val backgroundOffsetYFraction: Float = 0f,
    val showZoneMarkers: Boolean = true,
)

data class NewGameSetupState(
    val openingBasicType: TileType = TileType.ROSE,
    val selectedAccents: List<AccentType> = emptyList(),
) {
    val canStart: Boolean
        get() = openingBasicType.isBasic &&
            selectedAccents.size == 4 &&
            AccentType.entries.all { type -> selectedAccents.count { it == type } <= 2 }
}

data class GameUiState(
    val boardSize: Int = 9,
    val coordinateExtent: Int = 4,
    val currentPlayer: Player = Player.HUMAN,
    val isAwaitingSubmit: Boolean = false,
    val canSubmitTurn: Boolean = false,
    val canUndoTurn: Boolean = false,
    val canInteract: Boolean = true,
    val selectedSource: Position? = null,
    val selectedTarget: Position? = null,
    val legalTargets: Set<Position> = emptySet(),
    val legalPositions: Set<Position> = emptySet(),
    val zoneByPosition: Map<Position, BoardZone> = emptyMap(),
    val boardVisualConfig: BoardVisualConfig = BoardVisualConfig(),
    val selectedTileType: TileType? = null,
    val selectedAccentType: AccentType? = null,
    val flowerReserveCounts: Map<TileType, Int> = emptyMap(),
    val accentReserveCounts: Map<AccentType, Int> = emptyMap(),
    val stagedActions: List<String> = emptyList(),
    val isHarmonyBonusFlow: Boolean = false,
    val canChooseNoBonus: Boolean = false,
    val projectedBoardSnapshot: Map<Position, String> = emptyMap(),
    val harmonyBonusFlowerOptions: Set<TileType> = emptySet(),
    val harmonyBonusAccentOptions: Set<AccentType> = emptySet(),
    val boardSnapshot: Map<Position, String> = emptyMap(),
    val eventLog: List<String> = listOf("Welcome to Pai Sho."),
    val isGameOver: Boolean = false,
    val isDraw: Boolean = false,
    val winner: Player? = null,
    val endReason: GameEndReason? = null,
    val phase: GamePhase = GamePhase.PLAYING,
    val setupState: NewGameSetupState = NewGameSetupState(),
    val multiplayerSession: MultiplayerSession = MultiplayerSession(),
    val existingGames: List<ExistingGameSummary> = emptyList(),
    val appScreen: AppScreen = AppScreen.Home,
    val drawerSection: DrawerSection = DrawerSection.Game,
)
