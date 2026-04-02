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
    val config = environment.config
    install(ContentNegotiation) {
        json(JsonCodec.json)
    }

    val dbPath = System.getenv("PAISHO_DB_PATH")
        ?: config.propertyOrNull("paisho.db.path")?.getString()
        ?: "/data/paisho.db"
    val jwtSecret = System.getenv("PAISHO_JWT_SECRET")
        ?: config.propertyOrNull("paisho.jwt.secret")?.getString()
        ?: "dev-only-change-me"
    val tokenTtlSeconds = config.propertyOrNull("paisho.jwt.ttlSeconds")?.getString()?.toLongOrNull() ?: 7L * 24L * 3600L
    val tokenService = TokenService(secret = jwtSecret, ttlSeconds = tokenTtlSeconds)
    val service = GameService(Database(dbPath), tokenService)

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }

        route("/api/v1") {
            post("/auth/token") {
                runCatching {
                    val request = call.receive<LoginRequest>()
                    service.issueToken(request)
                }.onSuccess { response ->
                    call.respond(response)
                }.onFailure { t ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(t.message ?: "Unable to login"))
                }
            }

            post("/games") {
                val principal = runCatching { call.requireAuthPlayer(tokenService) }.getOrElse {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(it.message ?: "Unauthorized"))
                    return@post
                }
                runCatching {
                    val request = call.receive<CreateGameRequest>()
                    service.createGame(request = request, principal = principal)
                }.onSuccess { created ->
                    call.respond(HttpStatusCode.Created, created)
                }.onFailure { t ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(t.message ?: "Invalid request"))
                }
            }

            post("/games/{gameId}/join") {
                val principal = runCatching { call.requireAuthPlayer(tokenService) }.getOrElse {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(it.message ?: "Unauthorized"))
                    return@post
                }
                val gameId = call.parameters["gameId"].orEmpty()
                runCatching {
                    val request = call.receive<JoinGameRequest>()
                    service.joinGame(gameId = gameId, request = request, principal = principal)
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
                val principal = runCatching { call.requireAuthPlayer(tokenService) }.getOrElse {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(it.message ?: "Unauthorized"))
                    return@get
                }
                call.respond(service.listGames(principal.playerId))
            }

            get("/games/{gameId}") {
                val principal = runCatching { call.requireAuthPlayer(tokenService) }.getOrElse {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(it.message ?: "Unauthorized"))
                    return@get
                }
                val gameId = call.parameters["gameId"].orEmpty()
                val details = service.getGameForPlayerOrNull(gameId, principal.playerId)
                if (details == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Game not found"))
                    return@get
                }
                call.respond(details)
            }

            get("/games/{gameId}/legal-moves") {
                val principal = runCatching { call.requireAuthPlayer(tokenService) }.getOrElse {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(it.message ?: "Unauthorized"))
                    return@get
                }
                val gameId = call.parameters["gameId"].orEmpty()
                runCatching {
                    service.getLegalMoves(gameId, principal.playerId)
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
                val principal = runCatching { call.requireAuthPlayer(tokenService) }.getOrElse {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(it.message ?: "Unauthorized"))
                    return@post
                }
                val gameId = call.parameters["gameId"].orEmpty()
                runCatching {
                    val request = call.receive<SubmitMoveRequest>()
                    service.submitMove(gameId, request, principal.playerId)
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

