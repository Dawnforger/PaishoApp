package com.paisho.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import java.time.Instant
import java.util.Date
import java.util.UUID

data class AuthenticatedPlayer(
    val playerId: String,
)

class TokenService(
    secret: String,
    val ttlSeconds: Long,
) {
    private val issuer = "paisho-server"
    private val audience = "paisho-clients"
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun issueToken(playerId: String): String {
        val normalized = playerId.trim()
        require(normalized.isNotEmpty()) { "playerId cannot be blank" }
        val expiresAt = Instant.now().plusSeconds(ttlSeconds)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(normalized)
            .withJWTId(UUID.randomUUID().toString())
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
    }

    fun verifyToken(rawToken: String): AuthenticatedPlayer {
        val decoded = verifier.verify(rawToken)
        val playerId = decoded.subject?.trim().orEmpty()
        if (playerId.isEmpty()) {
            throw ApiException(HttpStatusCode.Unauthorized, "Invalid auth token")
        }
        return AuthenticatedPlayer(playerId = playerId)
    }
}

fun ApplicationCall.requireAuthPlayer(tokenService: TokenService): AuthenticatedPlayer {
    val authHeader = request.header("Authorization")
        ?: throw ApiException(HttpStatusCode.Unauthorized, "Missing Authorization header")
    if (!authHeader.startsWith("Bearer ")) {
        throw ApiException(HttpStatusCode.Unauthorized, "Authorization must use Bearer token")
    }
    val token = authHeader.removePrefix("Bearer ").trim()
    if (token.isEmpty()) {
        throw ApiException(HttpStatusCode.Unauthorized, "Authorization token is empty")
    }
    return runCatching { tokenService.verifyToken(token) }
        .getOrElse { throw ApiException(HttpStatusCode.Unauthorized, "Invalid auth token") }
}
