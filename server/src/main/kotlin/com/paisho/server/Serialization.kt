package com.paisho.server

import com.paisho.core.game.AccentTile
import com.paisho.core.game.AccentType
import com.paisho.core.game.FlowerTile
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GamePhase
import com.paisho.core.game.GameState
import com.paisho.core.game.Player
import com.paisho.core.game.PlayerReserve
import com.paisho.core.game.Position
import com.paisho.core.game.TileType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object JsonCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> toJson(value: T): String = json.encodeToString(value)
    inline fun <reified T> fromJson(raw: String): T = json.decodeFromString(raw)
}

@kotlinx.serialization.Serializable
data class SerializablePosition(val row: Int, val col: Int)

@kotlinx.serialization.Serializable
data class SerializableFlowerTile(
    val id: Int,
    val owner: String,
    val type: String,
    val position: SerializablePosition,
)

@kotlinx.serialization.Serializable
data class SerializableAccentTile(
    val id: Int,
    val owner: String,
    val type: String,
    val position: SerializablePosition,
)

@kotlinx.serialization.Serializable
data class SerializablePlayerReserve(
    val basic: Map<String, Int>,
    val special: Map<String, Int>,
    val accents: Map<String, Int>,
)

@kotlinx.serialization.Serializable
data class SerializableRulesConfig(
    val openingBasicType: String,
    val humanAccentLoadout: List<String>,
    val aiAccentLoadout: List<String>,
)

@kotlinx.serialization.Serializable
data class SerializableGameState(
    val rules: SerializableRulesConfig,
    val flowers: List<SerializableFlowerTile>,
    val accents: List<SerializableAccentTile>,
    val reserves: Map<String, SerializablePlayerReserve>,
    val currentPlayer: String,
    val phase: String,
    val winner: String? = null,
    val isDraw: Boolean,
    val endReason: String? = null,
    val nextFlowerId: Int,
    val nextAccentId: Int,
    val turnNumber: Int,
)

fun GameState.toSerializable(): SerializableGameState {
    return SerializableGameState(
        rules = SerializableRulesConfig(
            openingBasicType = rules.openingBasicType.name,
            humanAccentLoadout = rules.humanAccentLoadout.map { it.name },
            aiAccentLoadout = rules.aiAccentLoadout.map { it.name },
        ),
        flowers = flowers.map { tile ->
            SerializableFlowerTile(
                id = tile.id,
                owner = tile.owner.name,
                type = tile.type.name,
                position = SerializablePosition(tile.position.row, tile.position.col),
            )
        },
        accents = accents.map { tile ->
            SerializableAccentTile(
                id = tile.id,
                owner = tile.owner.name,
                type = tile.type.name,
                position = SerializablePosition(tile.position.row, tile.position.col),
            )
        },
        reserves = reserves.mapKeys { it.key.name }.mapValues { (_, reserve) ->
            SerializablePlayerReserve(
                basic = reserve.basic.mapKeys { it.key.name },
                special = reserve.special.mapKeys { it.key.name },
                accents = reserve.accents.mapKeys { it.key.name },
            )
        },
        currentPlayer = currentPlayer.name,
        phase = phase.name,
        winner = winner?.name,
        isDraw = isDraw,
        endReason = endReason?.name,
        nextFlowerId = nextFlowerId,
        nextAccentId = nextAccentId,
        turnNumber = turnNumber,
    )
}

fun SerializableGameState.toCore(): GameState {
    val rulesConfig = com.paisho.core.game.RulesConfig(
        openingBasicType = TileType.valueOf(rules.openingBasicType),
        humanAccentLoadout = rules.humanAccentLoadout.map { AccentType.valueOf(it) },
        aiAccentLoadout = rules.aiAccentLoadout.map { AccentType.valueOf(it) },
    )
    return GameState.initial(rulesConfig).copy(
        flowers = flowers.map { flower ->
            FlowerTile(
                id = flower.id,
                owner = Player.valueOf(flower.owner),
                type = TileType.valueOf(flower.type),
                position = Position(flower.position.row, flower.position.col),
            )
        },
        accents = accents.map { accent ->
            AccentTile(
                id = accent.id,
                owner = Player.valueOf(accent.owner),
                type = AccentType.valueOf(accent.type),
                position = Position(accent.position.row, accent.position.col),
            )
        },
        reserves = reserves.mapKeys { (player, _) -> Player.valueOf(player) }
            .mapValues { (_, reserve) ->
                PlayerReserve(
                    basic = reserve.basic.mapKeys { (tile, _) -> TileType.valueOf(tile) },
                    special = reserve.special.mapKeys { (tile, _) -> TileType.valueOf(tile) },
                    accents = reserve.accents.mapKeys { (accent, _) -> AccentType.valueOf(accent) },
                )
            },
        currentPlayer = Player.valueOf(currentPlayer),
        phase = GamePhase.valueOf(phase),
        winner = winner?.let { Player.valueOf(it) },
        isDraw = isDraw,
        endReason = endReason?.let { GameEndReason.valueOf(it) },
        nextFlowerId = nextFlowerId,
        nextAccentId = nextAccentId,
        turnNumber = turnNumber,
    )
}
