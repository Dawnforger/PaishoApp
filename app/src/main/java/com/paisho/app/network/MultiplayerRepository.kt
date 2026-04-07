package com.paisho.app.network

import com.paisho.core.game.AccentType
import com.paisho.core.game.TileType

class MultiplayerRepository(
    private val api: MultiplayerApi = MultiplayerApi(),
) {
    private var baseUrl: String = ""
    private var playerId: String = ""
    private var token: String? = null
    private var activeGameId: String? = null

    fun configure(baseUrl: String, playerId: String) {
        this.baseUrl = baseUrl.trim().trimEnd('/')
        this.playerId = playerId.trim()
        this.token = null
        this.activeGameId = null
    }

    suspend fun login(): Result<LoginResponseDto> = runCatching {
        require(baseUrl.isNotBlank()) { "Multiplayer base URL not configured." }
        require(playerId.isNotBlank()) { "Multiplayer player ID not configured." }
        val response = api.issueToken(baseUrl, playerId)
        token = response.token
        response
    }

    suspend fun createGame(
        openingBasicType: TileType,
        hostAccentLoadout: List<AccentType>,
        guestAccentLoadout: List<AccentType>,
        hostDisplayName: String? = null,
    ): Result<GameDetailsDto> = runCatching {
        val auth = token ?: error("Login required before creating online game.")
        val details = api.createGame(
            baseUrl = baseUrl,
            token = auth,
            request = CreateGameRequestDto(
                hostDisplayName = hostDisplayName,
                openingBasicType = openingBasicType,
                hostAccentLoadout = hostAccentLoadout,
                guestAccentLoadout = guestAccentLoadout,
            ),
        )
        activeGameId = details.summary.gameId
        details
    }

    suspend fun getGame(gameId: String = activeGameId ?: ""): Result<GameDetailsDto> = runCatching {
        val auth = token ?: error("Login required before fetching online game.")
        require(gameId.isNotBlank()) { "No online game selected." }
        val details = api.getGame(baseUrl = baseUrl, token = auth, gameId = gameId)
        activeGameId = details.summary.gameId
        details
    }

    suspend fun listGames(): Result<List<GameSummaryDto>> = runCatching {
        val auth = token ?: error("Login required before listing games.")
        api.listGames(baseUrl = baseUrl, token = auth).games
    }

    suspend fun joinGame(gameId: String): Result<GameDetailsDto> = runCatching {
        val auth = token ?: error("Login required before joining online game.")
        require(gameId.isNotBlank()) { "gameId cannot be blank." }
        val details = api.joinGame(
            baseUrl = baseUrl,
            token = auth,
            gameId = gameId,
            request = JoinGameRequestDto(),
        )
        activeGameId = details.summary.gameId
        details
    }
}
