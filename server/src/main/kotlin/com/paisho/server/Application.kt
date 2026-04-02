package com.paisho.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(JsonCodec.json)
    }

    val dbPath = System.getenv("PAISHO_DB_PATH") ?: "/data/paisho.db"
    val service = GameService(Database(dbPath))

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }

        route("/api/v1") {
            post("/games") {
                runCatching {
                    val request = call.receive<CreateGameRequest>()
                    service.createGame(request)
                }.onSuccess { created ->
                    call.respond(HttpStatusCode.Created, created)
                }.onFailure { t ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(t.message ?: "Invalid request"))
                }
            }

            post("/games/{gameId}/join") {
                val gameId = call.parameters["gameId"].orEmpty()
                runCatching {
                    val request = call.receive<JoinGameRequest>()
                    service.joinGame(gameId, request)
                }.onSuccess { details ->
                    call.respond(details)
                }.onFailure { t ->
                    val status = when {
                        t.message?.contains("not found", ignoreCase = true) == true -> HttpStatusCode.NotFound
                        t.message?.contains("already has", ignoreCase = true) == true -> HttpStatusCode.Conflict
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respond(status, ErrorResponse(t.message ?: "Unable to join game"))
                }
            }

            get("/games") {
                val playerId = call.request.queryParameters["playerId"]?.trim().orEmpty()
                if (playerId.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("playerId query param is required"))
                    return@get
                }
                call.respond(service.listGames(playerId))
            }

            get("/games/{gameId}") {
                val gameId = call.parameters["gameId"].orEmpty()
                val details = service.getGameOrNull(gameId)
                if (details == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Game not found"))
                    return@get
                }
                call.respond(details)
            }

            get("/games/{gameId}/legal-moves") {
                val gameId = call.parameters["gameId"].orEmpty()
                val playerId = call.request.queryParameters["playerId"]?.trim().orEmpty()
                if (playerId.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("playerId query param is required"))
                    return@get
                }
                runCatching {
                    service.getLegalMoves(gameId, playerId)
                }.onSuccess { legalMoves ->
                    call.respond(legalMoves)
                }.onFailure { t ->
                    val status = when {
                        t.message?.contains("not found", ignoreCase = true) == true -> HttpStatusCode.NotFound
                        t.message?.contains("not part of", ignoreCase = true) == true -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respond(status, ErrorResponse(t.message ?: "Unable to list legal moves"))
                }
            }

            post("/games/{gameId}/moves") {
                val gameId = call.parameters["gameId"].orEmpty()
                runCatching {
                    val request = call.receive<SubmitMoveRequest>()
                    service.submitMove(gameId, request)
                }.onSuccess { response ->
                    call.respond(response)
                }.onFailure { t ->
                    val status = when {
                        t.message?.contains("not found", ignoreCase = true) == true -> HttpStatusCode.NotFound
                        t.message?.contains("not your turn", ignoreCase = true) == true -> HttpStatusCode.Conflict
                        t.message?.contains("version", ignoreCase = true) == true -> HttpStatusCode.Conflict
                        t.message?.contains("Illegal move", ignoreCase = true) == true -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respond(status, ErrorResponse(t.message ?: "Unable to submit move"))
                }
            }
        }
    }
}

