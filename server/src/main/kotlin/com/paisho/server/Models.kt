package com.paisho.server

import com.paisho.core.game.AccentType
import com.paisho.core.game.BonusAction
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GamePhase
import com.paisho.core.game.GameState
import com.paisho.core.game.Move
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.TileType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String,
)

class ApiException(
    val status: HttpStatusCode,
    override val message: String,
) : RuntimeException(message)

@Serializable
data class HealthResponse(
    val status: String = "ok",
)

@Serializable
enum class GameStatus {
    WAITING_FOR_GUEST,
    IN_PROGRESS,
    FINISHED,
}

@Serializable
data class CreateGameRequest(
    val hostDisplayName: String? = null,
    val title: String? = null,
    val openingBasicType: TileType = TileType.ROSE,
    val hostAccentLoadout: List<AccentType> = listOf(
        AccentType.ROCK,
        AccentType.WHEEL,
        AccentType.KNOTWEED,
        AccentType.BOAT,
    ),
    val guestAccentLoadout: List<AccentType> = listOf(
        AccentType.ROCK,
        AccentType.WHEEL,
        AccentType.KNOTWEED,
        AccentType.BOAT,
    ),
)

@Serializable
data class JoinGameRequest(
    val guestDisplayName: String? = null,
)

@Serializable
data class SubmitMoveRequest(
    val expectedVersion: Int,
    val move: MoveDto,
)

@Serializable
data class LoginRequest(
    val playerId: String,
)

@Serializable
data class LoginResponse(
    val playerId: String,
    val token: String,
    val expiresAtEpochMs: Long,
)

@Serializable
data class GamesListResponse(
    val games: List<GameSummaryDto>,
)

@Serializable
data class LegalMovesResponse(
    val gameId: String,
    val legalMoves: List<MoveDto>,
)

@Serializable
data class SubmitMoveResponse(
    val game: GameDetailsDto,
    val acceptedMove: MoveDto,
    val nextLegalMoves: List<MoveDto>,
)

@Serializable
data class GameSummaryDto(
    val gameId: String,
    val title: String,
    val status: GameStatus,
    val hostPlayerId: String,
    val hostDisplayName: String? = null,
    val guestPlayerId: String? = null,
    val guestDisplayName: String? = null,
    val currentTurnPlayerId: String? = null,
    val winnerPlayerId: String? = null,
    val turnNumber: Int,
    val version: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Serializable
data class GameDetailsDto(
    val summary: GameSummaryDto,
    val state: ServerGameStateDto,
)

@Serializable
data class ServerGameStateDto(
    val currentPlayer: Player,
    val phase: GamePhase,
    val winner: Player? = null,
    val isDraw: Boolean,
    val endReason: GameEndReason? = null,
    val turnNumber: Int,
    val boardSnapshot: List<BoardCellDto>,
)

@Serializable
data class BoardCellDto(
    val position: PositionDto,
    val token: String,
)

@Serializable
data class PositionDto(
    val row: Int,
    val col: Int,
) {
    fun toCore(): Position = Position(row, col)

    companion object {
        fun fromCore(position: Position): PositionDto = PositionDto(position.row, position.col)
    }
}

@Serializable
data class MoveDto(
    val kind: String,
    val tileType: TileType? = null,
    val tileId: Int? = null,
    val target: PositionDto,
    val bonus: BonusActionDto? = null,
) {
    fun toCoreMove(): Move {
        return when (kind.lowercase()) {
            "plant" -> {
                val type = tileType ?: throw IllegalArgumentException("tileType required for plant move")
                Move.Plant(type = type, target = target.toCore())
            }

            "slide" -> {
                val id = tileId ?: throw IllegalArgumentException("tileId required for slide move")
                Move.Slide(
                    tileId = id,
                    target = target.toCore(),
                    bonus = bonus?.toCore(),
                )
            }

            else -> throw IllegalArgumentException("Unsupported move kind: $kind")
        }
    }

    companion object {
        fun fromCore(move: Move): MoveDto {
            return when (move) {
                is Move.Plant -> MoveDto(
                    kind = "plant",
                    tileType = move.type,
                    target = PositionDto.fromCore(move.target),
                )

                is Move.Slide -> MoveDto(
                    kind = "slide",
                    tileId = move.tileId,
                    target = PositionDto.fromCore(move.target),
                    bonus = move.bonus?.let { BonusActionDto.fromCore(it) },
                )
            }
        }
    }
}

@Serializable
data class BonusActionDto(
    val kind: String,
    val accentType: AccentType? = null,
    val target: PositionDto? = null,
    val source: PositionDto? = null,
    val destination: PositionDto? = null,
    val targetAccent: PositionDto? = null,
    val tileType: TileType? = null,
    val gate: PositionDto? = null,
) {
    fun toCore(): BonusAction {
        return when (kind.lowercase()) {
            "place_accent" -> BonusAction.PlaceAccent(
                type = accentType ?: throw IllegalArgumentException("accentType required for place_accent"),
                target = target?.toCore() ?: throw IllegalArgumentException("target required for place_accent"),
            )

            "boat_move" -> BonusAction.BoatMove(
                source = source?.toCore() ?: throw IllegalArgumentException("source required for boat_move"),
                destination = destination?.toCore()
                    ?: throw IllegalArgumentException("destination required for boat_move"),
            )

            "boat_remove" -> BonusAction.BoatRemoveAccent(
                targetAccent = targetAccent?.toCore()
                    ?: throw IllegalArgumentException("targetAccent required for boat_remove"),
            )

            "plant_bonus" -> BonusAction.PlantBonus(
                tileType = tileType ?: throw IllegalArgumentException("tileType required for plant_bonus"),
                gate = gate?.toCore() ?: throw IllegalArgumentException("gate required for plant_bonus"),
            )

            else -> throw IllegalArgumentException("Unsupported bonus kind: $kind")
        }
    }

    companion object {
        fun fromCore(action: BonusAction): BonusActionDto {
            return when (action) {
                is BonusAction.PlaceAccent -> BonusActionDto(
                    kind = "place_accent",
                    accentType = action.type,
                    target = PositionDto.fromCore(action.target),
                )

                is BonusAction.BoatMove -> BonusActionDto(
                    kind = "boat_move",
                    source = PositionDto.fromCore(action.source),
                    destination = PositionDto.fromCore(action.destination),
                )

                is BonusAction.BoatRemoveAccent -> BonusActionDto(
                    kind = "boat_remove",
                    targetAccent = PositionDto.fromCore(action.targetAccent),
                )

                is BonusAction.PlantBonus -> BonusActionDto(
                    kind = "plant_bonus",
                    tileType = action.tileType,
                    gate = PositionDto.fromCore(action.gate),
                )
            }
        }
    }
}

fun GameState.toStateDto(): ServerGameStateDto {
    val board = boardSnapshot()
        .entries
        .sortedWith(compareByDescending<Map.Entry<Position, String>> { it.key.row }.thenBy { it.key.col })
        .map { (pos, token) -> BoardCellDto(position = PositionDto.fromCore(pos), token = token) }
    return ServerGameStateDto(
        currentPlayer = currentPlayer,
        phase = phase,
        winner = winner,
        isDraw = isDraw,
        endReason = endReason,
        turnNumber = turnNumber,
        boardSnapshot = board,
    )
}
