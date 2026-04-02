package com.paisho.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun inMemoryDbUrl(name: String): String = "jdbc:sqlite:file:$name?mode=memory&cache=shared"

    @Test
    fun `create join and get legal moves with auth`() = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig(
                "paisho.db.path" to inMemoryDbUrl("test-create-join"),
                "paisho.jwt.secret" to "test-secret",
            )
        }
        application {
            module()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        }

        val hostTokenResp = client.post("/api/v1/auth/token") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(playerId = "host-1"))
        }
        assertEquals(HttpStatusCode.OK, hostTokenResp.status)
        val hostToken = json.decodeFromString<LoginResponse>(hostTokenResp.bodyAsText()).token

        val createResp = client.post("/api/v1/games") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            setBody(
                json.encodeToString(
                    CreateGameRequest(
                        hostDisplayName = "Host",
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val created = json.decodeFromString<GameDetailsDto>(createResp.bodyAsText())
        val gameId = created.summary.gameId

        val guestTokenResp = client.post("/api/v1/auth/token") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(playerId = "guest-1"))
        }
        val guestToken = json.decodeFromString<LoginResponse>(guestTokenResp.bodyAsText()).token

        val joinResp = client.post("/api/v1/games/$gameId/join") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $guestToken")
            setBody(
                json.encodeToString(
                    JoinGameRequest(guestDisplayName = "Guest")
                )
            )
        }
        assertEquals(HttpStatusCode.OK, joinResp.status)

        val legalResp = client.get("/api/v1/games/$gameId/legal-moves") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, legalResp.status)
        val legal = json.decodeFromString<LegalMovesResponse>(legalResp.bodyAsText())
        assertTrue(legal.legalMoves.isNotEmpty())
    }

    @Test
    fun `legal moves forbidden for non member`() = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig(
                "paisho.db.path" to inMemoryDbUrl("test-forbidden"),
                "paisho.jwt.secret" to "test-secret",
            )
        }
        application {
            module()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        }

        val hostToken = json.decodeFromString<LoginResponse>(
            client.post("/api/v1/auth/token") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(playerId = "host-2"))
            }.bodyAsText()
        ).token

        val created = json.decodeFromString<GameDetailsDto>(
            client.post("/api/v1/games") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $hostToken")
                setBody(json.encodeToString(CreateGameRequest()))
            }.bodyAsText()
        )

        val outsiderToken = json.decodeFromString<LoginResponse>(
            client.post("/api/v1/auth/token") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(playerId = "outsider"))
            }.bodyAsText()
        ).token

        val response = client.get("/api/v1/games/${created.summary.gameId}/legal-moves") {
            header(HttpHeaders.Authorization, "Bearer $outsiderToken")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `submit move with stale version returns conflict`() = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig(
                "paisho.db.path" to inMemoryDbUrl("test-stale-version"),
                "paisho.jwt.secret" to "test-secret",
            )
        }
        application {
            module()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
        }

        val hostToken = json.decodeFromString<LoginResponse>(
            client.post("/api/v1/auth/token") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(playerId = "host-3"))
            }.bodyAsText()
        ).token

        val created = json.decodeFromString<GameDetailsDto>(
            client.post("/api/v1/games") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $hostToken")
                setBody(json.encodeToString(CreateGameRequest()))
            }.bodyAsText()
        )

        val gameId = created.summary.gameId

        val legalMoves = json.decodeFromString<LegalMovesResponse>(
            client.get("/api/v1/games/$gameId/legal-moves") {
                header(HttpHeaders.Authorization, "Bearer $hostToken")
            }.bodyAsText()
        ).legalMoves
        val firstMove = legalMoves.first()

        val firstSubmit = client.post("/api/v1/games/$gameId/moves") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            setBody(
                json.encodeToString(
                    SubmitMoveRequest(expectedVersion = 1, move = firstMove)
                )
            )
        }
        assertEquals(HttpStatusCode.OK, firstSubmit.status)

        val secondSubmitStale = client.post("/api/v1/games/$gameId/moves") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            setBody(
                json.encodeToString(
                    SubmitMoveRequest(expectedVersion = 1, move = firstMove)
                )
            )
        }
        assertEquals(HttpStatusCode.Conflict, secondSubmitStale.status)
    }
}
