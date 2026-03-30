package com.paisho.core.game

data class Position(val row: Int, val col: Int) {
    fun isInsideBoard(size: Int): Boolean {
        val extent = size / 2
        return row in -extent..extent && col in -extent..extent
    }

    fun isEdge(size: Int): Boolean {
        val extent = size / 2
        return kotlin.math.abs(row) == extent || kotlin.math.abs(col) == extent
    }

    fun surrounding8(): List<Position> = buildList {
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                add(Position(row + dr, col + dc))
            }
        }
    }
}

enum class Player {
    HUMAN,
    AI;

    fun other(): Player = if (this == HUMAN) AI else HUMAN
}

enum class GardenColor {
    RED,
    WHITE,
    NEUTRAL,
}

enum class BoardZone {
    BORDER,
    GATE,
    RED_GARDEN,
    WHITE_GARDEN,
    NEUTRAL_GARDEN,
}

object SkudBoardLayout {
    const val COORD_EXTENT: Int = 8
    const val BOARD_DIAMETER: Int = COORD_EXTENT * 2 + 1

    /**
     * Legal coordinates match the octagonal playable footprint shown in the coordinate reference:
     * all points where abs(x) + abs(y) <= 12, with x/y constrained to [-8, 8].
     */
    val legalPositions: Set<Position> = buildSet {
        for (x in -COORD_EXTENT..COORD_EXTENT) {
            for (y in -COORD_EXTENT..COORD_EXTENT) {
                if (kotlin.math.abs(x) + kotlin.math.abs(y) <= 12) {
                    add(Position(x, y))
                }
            }
        }
    }

    val gates: Set<Position> = setOf(
        Position(0, COORD_EXTENT),
        Position(COORD_EXTENT, 0),
        Position(0, -COORD_EXTENT),
        Position(-COORD_EXTENT, 0),
    )

    val zoneByPosition: Map<Position, BoardZone> = legalPositions.associateWith { p ->
        when {
            p in gates -> BoardZone.GATE
            p.row == 0 || p.col == 0 -> BoardZone.BORDER
            p.row * p.col > 0 && kotlin.math.abs(p.row) + kotlin.math.abs(p.col) <= 6 -> BoardZone.RED_GARDEN
            p.row * p.col < 0 && kotlin.math.abs(p.row) + kotlin.math.abs(p.col) <= 6 -> BoardZone.WHITE_GARDEN
            else -> BoardZone.NEUTRAL_GARDEN
        }
    }
}

enum class TileType(
    val maxMoveDistance: Int,
    val shortName: String,
    val isBasic: Boolean,
    val isSpecial: Boolean,
    val basicColor: GardenColor?,
) {
    // Basic Flowers
    ROSE(3, "R3", true, false, GardenColor.RED),
    CHRYSANTHEMUM(4, "R4", true, false, GardenColor.RED),
    RHODODENDRON(5, "R5", true, false, GardenColor.RED),
    JASMINE(3, "W3", true, false, GardenColor.WHITE),
    LILY(4, "W4", true, false, GardenColor.WHITE),
    WHITE_JADE(5, "W5", true, false, GardenColor.WHITE),
    // Special Flowers
    WHITE_LOTUS(2, "WL", false, true, null),
    ORCHID(6, "OR", false, true, null);

    val isRedBasic: Boolean get() = isBasic && basicColor == GardenColor.RED
    val isWhiteBasic: Boolean get() = isBasic && basicColor == GardenColor.WHITE
    fun sameNumberAs(other: TileType): Boolean = maxMoveDistance == other.maxMoveDistance

    companion object {
        val basicTypes: List<TileType> = entries.filter { it.isBasic }
        val specialTypes: List<TileType> = entries.filter { it.isSpecial }
    }
}

enum class AccentType(val shortName: String) {
    ROCK("RK"),
    WHEEL("WH"),
    KNOTWEED("KN"),
    BOAT("BT"),
}

data class FlowerTile(
    val id: Int,
    val owner: Player,
    val type: TileType,
    val position: Position,
)

data class AccentTile(
    val id: Int,
    val owner: Player,
    val type: AccentType,
    val position: Position,
)

enum class GamePhase {
    PLAYING,
    FINISHED,
}

enum class GameEndReason {
    HARMONY_RING,
    LAST_BASIC_PLAYED,
    FORCED_DRAW,
}

data class Harmony(
    val owner: Player,
    val a: Position,
    val b: Position,
) {
    val key: Pair<Position, Position> = if (a.row < b.row || (a.row == b.row && a.col <= b.col)) {
        a to b
    } else {
        b to a
    }
}

sealed interface BonusAction {
    data class PlaceAccent(val type: AccentType, val target: Position) : BonusAction
    data class BoatMove(val source: Position, val destination: Position) : BonusAction
    data class BoatRemoveAccent(val targetAccent: Position) : BonusAction
    data class PlantBonus(val tileType: TileType, val gate: Position) : BonusAction
}

data class MovePreview(
    val move: Move,
    val willFormNewHarmony: Boolean,
    val legalBonuses: List<BonusAction>,
)

sealed interface Move {
    data class Plant(val type: TileType, val target: Position) : Move
    data class Slide(
        val tileId: Int,
        val target: Position,
        val bonus: BonusAction? = null,
    ) : Move
}

data class PlayerReserve(
    val basic: Map<TileType, Int>,
    val special: Map<TileType, Int>,
    val accents: Map<AccentType, Int>,
) {
    fun basicCount(type: TileType): Int = basic[type] ?: 0
    fun specialCount(type: TileType): Int = special[type] ?: 0
    fun accentCount(type: AccentType): Int = accents[type] ?: 0
    fun totalBasicCount(): Int = basic.values.sum()

    fun spendBasic(type: TileType): PlayerReserve {
        val current = basicCount(type)
        require(type.isBasic && current > 0) { "No basic tile left: $type" }
        return copy(basic = basic + (type to (current - 1)))
    }

    fun spendSpecial(type: TileType): PlayerReserve {
        val current = specialCount(type)
        require(type.isSpecial && current > 0) { "No special tile left: $type" }
        return copy(special = special + (type to (current - 1)))
    }

    fun spendAccent(type: AccentType): PlayerReserve {
        val current = accentCount(type)
        require(current > 0) { "No accent left: $type" }
        return copy(accents = accents + (type to (current - 1)))
    }
}

data class RulesConfig(
    val coordinateExtent: Int = SkudBoardLayout.COORD_EXTENT,
    val boardSize: Int = SkudBoardLayout.BOARD_DIAMETER,
    val openingBasicType: TileType = TileType.ROSE,
    // Traditional orientation: host at bottom gate, guest at top gate.
    val humanStartGate: Position = Position(-SkudBoardLayout.COORD_EXTENT, 0),
    val aiStartGate: Position = Position(SkudBoardLayout.COORD_EXTENT, 0),
    val legalPositions: Set<Position> = SkudBoardLayout.legalPositions,
    val zoneByPosition: Map<Position, BoardZone> = SkudBoardLayout.zoneByPosition,
    val gates: Set<Position> = SkudBoardLayout.gates,
    val humanAccentLoadout: List<AccentType> = listOf(
        AccentType.ROCK,
        AccentType.WHEEL,
        AccentType.KNOTWEED,
        AccentType.BOAT,
    ),
    val aiAccentLoadout: List<AccentType> = listOf(
        AccentType.ROCK,
        AccentType.WHEEL,
        AccentType.KNOTWEED,
        AccentType.BOAT,
    ),
)

data class GameState(
    val rules: RulesConfig = RulesConfig(),
    val flowers: List<FlowerTile>,
    val accents: List<AccentTile>,
    val reserves: Map<Player, PlayerReserve>,
    val currentPlayer: Player = Player.HUMAN,
    val phase: GamePhase = GamePhase.PLAYING,
    val winner: Player? = null,
    val isDraw: Boolean = false,
    val endReason: GameEndReason? = null,
    val nextFlowerId: Int = 1,
    val nextAccentId: Int = 1,
    val turnNumber: Int = 1,
) {
    fun reserveFor(player: Player): PlayerReserve = reserves.getValue(player)
    fun flowerAt(position: Position): FlowerTile? = flowers.firstOrNull { it.position == position }
    fun accentAt(position: Position): AccentTile? = accents.firstOrNull { it.position == position }
    fun isOnBoard(position: Position): Boolean = position in rules.legalPositions
    fun isGate(position: Position): Boolean = position in rules.gates
    fun isOccupied(position: Position): Boolean = flowerAt(position) != null || accentAt(position) != null
    fun flowersFor(player: Player): List<FlowerTile> = flowers.filter { it.owner == player }
    fun zoneAt(position: Position): BoardZone? = rules.zoneByPosition[position]

    fun gardenColor(position: Position): GardenColor {
        return when (zoneAt(position)) {
            BoardZone.RED_GARDEN -> GardenColor.RED
            BoardZone.WHITE_GARDEN -> GardenColor.WHITE
            else -> GardenColor.NEUTRAL
        }
    }

    fun hasGrowingFlower(player: Player): Boolean = flowersFor(player).any { isGate(it.position) }

    fun hasBloomingWhiteLotus(player: Player): Boolean = flowersFor(player)
        .any { it.type == TileType.WHITE_LOTUS && !isGate(it.position) }

    fun boardSnapshot(): Map<Position, String> {
        val map = mutableMapOf<Position, String>()
        accents.forEach { accent ->
            val prefix = if (accent.owner == Player.HUMAN) "H" else "A"
            map[accent.position] = prefix + accent.type.shortName
        }
        flowers.forEach { flower ->
            val prefix = if (flower.owner == Player.HUMAN) "H" else "A"
            map[flower.position] = prefix + flower.type.shortName
        }
        return map
    }

    companion object {
        fun initial(config: RulesConfig = RulesConfig()): GameState {
            require(config.openingBasicType.isBasic) { "Opening tile must be a Basic Flower tile." }
            require(config.gates.all { it in config.legalPositions }) { "All gates must be legal board coordinates." }
            require(config.humanStartGate in config.gates && config.aiStartGate in config.gates) {
                "Starting gates must be valid gate positions."
            }
            require(config.humanStartGate != config.aiStartGate) { "Starting gates must be opposite and distinct." }
            require(config.humanAccentLoadout.size == 4 && config.aiAccentLoadout.size == 4) {
                "Each player must choose exactly 4 Accent Tiles for the game."
            }

            val humanReserve = initialReserve(config.humanAccentLoadout).spendBasic(config.openingBasicType)
            val aiReserve = initialReserve(config.aiAccentLoadout).spendBasic(config.openingBasicType)
            val openingFlowers = listOf(
                FlowerTile(
                    id = 1,
                    owner = Player.HUMAN,
                    type = config.openingBasicType,
                    position = config.humanStartGate,
                ),
                FlowerTile(
                    id = 2,
                    owner = Player.AI,
                    type = config.openingBasicType,
                    position = config.aiStartGate,
                ),
            )

            return GameState(
                rules = config,
                flowers = openingFlowers,
                accents = emptyList(),
                reserves = mapOf(
                    Player.HUMAN to humanReserve,
                    Player.AI to aiReserve,
                ),
                currentPlayer = Player.HUMAN,
                phase = GamePhase.PLAYING,
                winner = null,
                isDraw = false,
                endReason = null,
                nextFlowerId = 3,
                nextAccentId = 1,
                turnNumber = 1,
            )
        }

        private fun initialReserve(loadout: List<AccentType>): PlayerReserve {
            val basic = TileType.basicTypes.associateWith { 3 }
            val special = TileType.specialTypes.associateWith { 1 }
            val accents = AccentType.entries.associateWith { type -> loadout.count { it == type } }
            return PlayerReserve(
                basic = basic,
                special = special,
                accents = accents,
            )
        }
    }
}
