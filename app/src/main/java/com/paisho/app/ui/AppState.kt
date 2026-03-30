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
    ExistingGames,
    Settings,
}

enum class DrawerSection {
    Game,
    ExistingGames,
    Settings,
}

data class ExistingGameSummary(
    val id: String,
    val title: String,
    val subtitle: String,
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

data class BonusChoiceUi(
    val index: Int,
    val label: String,
)

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
    val pendingBonusChoices: List<BonusChoiceUi> = emptyList(),
    val boardSnapshot: Map<Position, String> = emptyMap(),
    val eventLog: List<String> = listOf("Welcome to Pai Sho."),
    val isGameOver: Boolean = false,
    val isDraw: Boolean = false,
    val winner: Player? = null,
    val endReason: GameEndReason? = null,
    val phase: GamePhase = GamePhase.PLAYING,
    val setupState: NewGameSetupState = NewGameSetupState(),
    val existingGames: List<ExistingGameSummary> = emptyList(),
    val appScreen: AppScreen = AppScreen.Home,
    val drawerSection: DrawerSection = DrawerSection.Game,
)
