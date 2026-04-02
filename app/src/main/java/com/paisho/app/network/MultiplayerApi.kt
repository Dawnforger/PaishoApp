package com.paisho.app.network

import com.paisho.core.game.AccentType
import com.paisho.core.game.GameEndReason
import com.paisho.core.game.GamePhase
import com.paisho.core.game.Player
import com.paisho.core.game.TileType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class LoginRequestDto(
    val playerId: String,
)

@Serializable
data class LoginResponseDto(
    val playerId: String,
    val token: String,
    val expiresAtEpochMs: Long,
)

@Serializable
data class CreateGameRequestDto(
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
data class JoinGameRequestDto(
    val guestDisplayName: String? = null,
)

@Serializable
data class SubmitMoveRequestDto(
    val expectedVersion: Int,
    val move: MoveDto,
)

@Serializable
data class GamesListResponseDto(
    val games: List<GameSummaryDto>,
)

@Serializable
data class GameSummaryDto(
    val gameId: String,
    val title: String,
    val status: GameStatusDto,
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
enum class GameStatusDto {
    WAITING_FOR_GUEST,
    IN_PROGRESS,
    FINISHED,
}

@Serializable
data class GameDetailsDto(
    val summary: GameSummaryDto,
    val state: ServerGameStateDto,
)

@Serializable
data class LegalMovesResponseDto(
    val gameId: String,
    val legalMoves: List<MoveDto>,
)

@Serializable
data class SubmitMoveResponseDto(
    val game: GameDetailsDto,
    val acceptedMove: MoveDto,
    val nextLegalMoves: List<MoveDto>,
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
)

@Serializable
data class MoveDto(
    val kind: String,
    val tileType: TileType? = null,
    val tileId: Int? = null,
    val target: PositionDto,
    val bonus: BonusActionDto? = null,
)

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
)

class MultiplayerApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun issueToken(baseUrl: String, playerId: String): LoginResponseDto {
        val requestBody = json.encodeToString(LoginRequestDto(playerId)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/auth/token")
            .post(requestBody)
            .build()
        return execute(request)
    }

    suspend fun createGame(baseUrl: String, token: String, request: CreateGameRequestDto): GameDetailsDto {
        val requestBody = json.encodeToString(request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/games")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        return execute(httpRequest)
    }

    suspend fun joinGame(baseUrl: String, token: String, gameId: String, request: JoinGameRequestDto): GameDetailsDto {
        val requestBody = json.encodeToString(request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/games/$gameId/join")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        return execute(httpRequest)
    }

    suspend fun listGames(baseUrl: String, token: String): GamesListResponseDto {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/games")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return execute(request)
    }

    suspend fun getGame(baseUrl: String, token: String, gameId: String): GameDetailsDto {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/games/$gameId")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return execute(request)
    }

    suspend fun getLegalMoves(baseUrl: String, token: String, gameId: String): LegalMovesResponseDto {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/games/$gameId/legal-moves")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return execute(request)
    }

    suspend fun submitMove(baseUrl: String, token: String, gameId: String, request: SubmitMoveRequestDto): SubmitMoveResponseDto {
        val requestBody = json.encodeToString(request).toRequestBody(jsonMediaType)
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/games/$gameId/moves")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        return execute(httpRequest)
    }

    private inline fun <reified T> decode(body: String): T = json.decodeFromString(body)

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { decode<ErrorResponseDto>(body).message }.getOrDefault(body.ifBlank { "HTTP ${response.code}" })
                throw IllegalStateException(message)
            }
            return decode(body)
        }
    }
}

@Serializable
data class ErrorResponseDto(
    val message: String,
)
