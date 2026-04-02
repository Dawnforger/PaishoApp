package com.paisho.server

import com.paisho.core.game.GamePhase
import com.paisho.core.game.GameState
import com.paisho.core.game.Player
import com.paisho.core.game.Rules
import com.paisho.core.game.RulesConfig

class GameService(
    private val db: Database,
) {
    fun createGame(request: CreateGameRequest): GameDetailsDto {
        val hostPlayerId = request.hostPlayerId.trim()
        require(hostPlayerId.isNotEmpty()) { "hostPlayerId cannot be blank" }

        val rules = RulesConfig(
            openingBasicType = request.openingBasicType,
            humanAccentLoadout = request.hostAccentLoadout,
            aiAccentLoadout = request.guestAccentLoadout,
        )
        val initialState = GameState.initial(rules)
        val created = db.createGame(
            hostPlayerId = hostPlayerId,
            initialStateJson = JsonCodec.toJson(initialState.toSerializable()),
        )
        return toDetails(created)
    }

    fun joinGame(gameId: String, request: JoinGameRequest): GameDetailsDto {
        val guestPlayerId = request.guestPlayerId.trim()
        require(guestPlayerId.isNotEmpty()) { "guestPlayerId cannot be blank" }
        val joined = db.joinGame(gameId, guestPlayerId)
            ?: throw IllegalArgumentException("Game not found or already has a different guest")
        return toDetails(joined)
    }

    fun listGames(playerId: String): GamesListResponse {
        val normalized = playerId.trim()
        require(normalized.isNotEmpty()) { "playerId cannot be blank" }
        return GamesListResponse(games = db.listGamesForPlayer(normalized).map(::toSummary))
    }

    fun getGameOrNull(gameId: String): GameDetailsDto? {
        return db.getGame(gameId)?.let(::toDetails)
    }

    fun getLegalMoves(gameId: String, playerId: String): LegalMovesResponse {
        val game = db.getGame(gameId) ?: throw IllegalArgumentException("Game not found")
        val actor = actorFor(game, playerId)
            ?: throw IllegalArgumentException("playerId is not part of this game")
        val state = decodeState(game.stateJson)
        val legalMoves = if (state.phase == GamePhase.FINISHED || state.currentPlayer != actor) {
            emptyList()
        } else {
            Rules.legalMoves(state).map { MoveDto.fromCore(it) }
        }
        return LegalMovesResponse(
            gameId = game.gameId,
            playerId = playerId,
            legalMoves = legalMoves,
        )
    }

    fun submitMove(gameId: String, request: SubmitMoveRequest): SubmitMoveResponse {
        val game = db.getGame(gameId) ?: throw IllegalArgumentException("Game not found")
        val actor = actorFor(game, request.playerId)
            ?: throw IllegalArgumentException("playerId is not part of this game")
        if (game.version != request.expectedVersion) {
            throw IllegalStateException("Version mismatch")
        }

        val state = decodeState(game.stateJson)
        if (state.currentPlayer != actor) {
            throw IllegalStateException("It is not your turn")
        }

        val move = request.move.toCoreMove()
        val legalMoves = Rules.legalMoves(state)
        if (move !in legalMoves) {
            throw IllegalArgumentException("Illegal move for current board state")
        }

        val nextState = Rules.applyMove(state, move)
        val updated = db.appendMoveAndUpdateState(
            gameId = game.gameId,
            actorPlayerId = request.playerId,
            expectedVersion = request.expectedVersion,
            movePayloadJson = JsonCodec.toJson(request.move),
            nextStateJson = JsonCodec.toJson(nextState.toSerializable()),
        ) ?: throw IllegalStateException("Version conflict while applying move")

        val updatedState = decodeState(updated.stateJson)
        val nextLegalMoves = if (updatedState.phase == GamePhase.FINISHED) {
            emptyList()
        } else {
            Rules.legalMoves(updatedState).map { MoveDto.fromCore(it) }
        }

        return SubmitMoveResponse(
            game = toDetails(updated),
            acceptedMove = request.move,
            nextLegalMoves = nextLegalMoves,
        )
    }

    private fun actorFor(game: StoredGame, playerId: String): Player? {
        val normalized = playerId.trim()
        return when (normalized) {
            game.hostPlayerId -> Player.HUMAN
            game.guestPlayerId -> Player.AI
            else -> null
        }
    }

    private fun toSummary(game: StoredGame): GameSummaryDto {
        val state = decodeState(game.stateJson)
        val status = when {
            game.guestPlayerId == null -> GameStatus.WAITING_FOR_GUEST
            state.phase == GamePhase.FINISHED -> GameStatus.FINISHED
            else -> GameStatus.IN_PROGRESS
        }
        val winnerPlayerId = when (state.winner) {
            Player.HUMAN -> game.hostPlayerId
            Player.AI -> game.guestPlayerId
            null -> null
        }
        val currentTurnPlayerId = when (state.currentPlayer) {
            Player.HUMAN -> game.hostPlayerId
            Player.AI -> game.guestPlayerId
        }
        val title = "Game ${game.gameId.take(8)}"
        return GameSummaryDto(
            gameId = game.gameId,
            title = title,
            status = status,
            hostPlayerId = game.hostPlayerId,
            guestPlayerId = game.guestPlayerId,
            currentTurnPlayerId = currentTurnPlayerId,
            winnerPlayerId = winnerPlayerId,
            turnNumber = state.turnNumber,
            version = game.version,
            createdAtEpochMs = game.createdAt.toEpochMilli(),
            updatedAtEpochMs = game.updatedAt.toEpochMilli(),
        )
    }

    private fun toDetails(game: StoredGame): GameDetailsDto {
        val state = decodeState(game.stateJson)
        return GameDetailsDto(
            summary = toSummary(game),
            state = state.toStateDto(),
        )
    }

    private fun decodeState(raw: String): GameState {
        return JsonCodec.fromJson<SerializableGameState>(raw).toCore()
    }
}
