package com.paisho.core.game

data class Position(val row: Int, val col: Int) {
    fun isInsideBoard(size: Int): Boolean = row in 0 until size && col in 0 until size
    fun isEdge(size: Int): Boolean = row == 0 || col == 0 || row == size - 1 || col == size - 1
}

enum class Player {
    HUMAN,
    AI;

    fun other(): Player = if (this == HUMAN) AI else HUMAN
}

enum class TileType(val maxMoveDistance: Int, val value: Int, val shortName: String) {
    ROSE(1, 1, "R"),
    JASMINE(2, 2, "J"),
    LILY(3, 3, "L"),
    WHITE_LOTUS(2, 4, "W"),
    ORCHID(4, 5, "O"),
}

data class Tile(
    val id: Int,
    val owner: Player,
    val type: TileType,
    val position: Position,
)

enum class GamePhase {
    PLANTING,
    MOVEMENT,
    FINISHED,
}

sealed interface Move {
    data class Plant(val type: TileType, val target: Position) : Move
    data class Slide(val tileId: Int, val target: Position) : Move
}

data class RulesConfig(
    val boardSize: Int = 9,
    val maxActiveTilesPerPlayer: Int = 8,
    val openingPlantTurnsPerPlayer: Int = 2,
)

data class GameState(
    val rules: RulesConfig = RulesConfig(),
    val tiles: List<Tile> = emptyList(),
    val currentPlayer: Player = Player.HUMAN,
    val phase: GamePhase = GamePhase.PLANTING,
    val plantsUsedHuman: Int = 0,
    val plantsUsedAi: Int = 0,
    val winner: Player? = null,
    val nextTileId: Int = 1,
    val turnNumber: Int = 1,
) {
    fun tileAt(position: Position): Tile? = tiles.firstOrNull { it.position == position }
    fun tilesFor(player: Player): List<Tile> = tiles.filter { it.owner == player }
    fun isOnBoard(position: Position): Boolean = position.isInsideBoard(rules.boardSize)

    fun boardSnapshot(): Map<Position, String> = tiles.associate { tile ->
        val prefix = if (tile.owner == Player.HUMAN) "H" else "A"
        tile.position to (prefix + tile.type.shortName)
    }

    companion object {
        fun initial(config: RulesConfig = RulesConfig()): GameState = GameState(rules = config)
    }
}
