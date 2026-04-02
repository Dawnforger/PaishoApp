package com.paisho.server

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

data class StoredGame(
    val gameId: String,
    val hostPlayerId: String,
    val guestPlayerId: String?,
    val stateJson: String,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class StoredMoveEvent(
    val id: Long,
    val gameId: String,
    val actorPlayerId: String,
    val payloadJson: String,
    val createdAt: Instant,
)

class Database(dbPath: String) {
    private val jdbcUrl = if (dbPath.startsWith("jdbc:")) dbPath else "jdbc:sqlite:$dbPath"
    private val connection: Connection = DriverManager.getConnection(jdbcUrl)

    init {
        connection.autoCommit = true
        initializeSchema()
    }

    private fun initializeSchema() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS games (
                    game_id TEXT PRIMARY KEY,
                    host_player_id TEXT NOT NULL,
                    guest_player_id TEXT,
                    state_json TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS move_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    game_id TEXT NOT NULL,
                    actor_player_id TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (game_id) REFERENCES games(game_id)
                )
                """.trimIndent()
            )
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_games_updated_at ON games(updated_at)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_moves_game_id ON move_events(game_id)")
        }
    }

    fun createGame(
        hostPlayerId: String,
        initialStateJson: String,
    ): StoredGame {
        val gameId = UUID.randomUUID().toString()
        val now = Instant.now()
        connection.prepareStatement(
            """
            INSERT INTO games (game_id, host_player_id, guest_player_id, state_json, version, created_at, updated_at)
            VALUES (?, ?, NULL, ?, 1, ?, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, gameId)
            ps.setString(2, hostPlayerId)
            ps.setString(3, initialStateJson)
            ps.setString(4, now.toString())
            ps.setString(5, now.toString())
            ps.executeUpdate()
        }
        return StoredGame(
            gameId = gameId,
            hostPlayerId = hostPlayerId,
            guestPlayerId = null,
            stateJson = initialStateJson,
            version = 1,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun joinGame(gameId: String, guestPlayerId: String): StoredGame? {
        val current = getGame(gameId) ?: return null
        if (current.guestPlayerId != null && current.guestPlayerId != guestPlayerId) return null
        if (current.hostPlayerId == guestPlayerId) return null

        val now = Instant.now()
        connection.prepareStatement(
            """
            UPDATE games
            SET guest_player_id = ?, updated_at = ?
            WHERE game_id = ? AND guest_player_id IS NULL
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, guestPlayerId)
            ps.setString(2, now.toString())
            ps.setString(3, gameId)
            ps.executeUpdate()
        }
        return getGame(gameId)
    }

    fun getGame(gameId: String): StoredGame? {
        connection.prepareStatement(
            """
            SELECT game_id, host_player_id, guest_player_id, state_json, version, created_at, updated_at
            FROM games
            WHERE game_id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, gameId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return rs.toStoredGame()
            }
        }
    }

    fun listGamesForPlayer(playerId: String, limit: Int = 50): List<StoredGame> {
        connection.prepareStatement(
            """
            SELECT game_id, host_player_id, guest_player_id, state_json, version, created_at, updated_at
            FROM games
            WHERE host_player_id = ? OR guest_player_id = ?
            ORDER BY updated_at DESC
            LIMIT ?
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, playerId)
            ps.setString(2, playerId)
            ps.setInt(3, limit.coerceIn(1, 200))
            ps.executeQuery().use { rs ->
                val rows = mutableListOf<StoredGame>()
                while (rs.next()) rows += rs.toStoredGame()
                return rows
            }
        }
    }

    fun appendMoveAndUpdateState(
        gameId: String,
        actorPlayerId: String,
        expectedVersion: Int,
        movePayloadJson: String,
        nextStateJson: String,
    ): StoredGame? {
        val existing = getGame(gameId) ?: return null
        if (existing.version != expectedVersion) return null

        val now = Instant.now()
        connection.autoCommit = false
        try {
            connection.prepareStatement(
                """
                INSERT INTO move_events (game_id, actor_player_id, payload_json, created_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, gameId)
                ps.setString(2, actorPlayerId)
                ps.setString(3, movePayloadJson)
                ps.setString(4, now.toString())
                ps.executeUpdate()
            }

            val updatedRows = connection.prepareStatement(
                """
                UPDATE games
                SET state_json = ?, version = ?, updated_at = ?
                WHERE game_id = ? AND version = ?
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, nextStateJson)
                ps.setInt(2, expectedVersion + 1)
                ps.setString(3, now.toString())
                ps.setString(4, gameId)
                ps.setInt(5, expectedVersion)
                ps.executeUpdate()
            }

            if (updatedRows != 1) {
                connection.rollback()
                connection.autoCommit = true
                return null
            }

            connection.commit()
            connection.autoCommit = true
            return getGame(gameId)
        } catch (t: Throwable) {
            connection.rollback()
            connection.autoCommit = true
            throw t
        }
    }

    fun moveHistory(gameId: String): List<StoredMoveEvent> {
        connection.prepareStatement(
            """
            SELECT id, game_id, actor_player_id, payload_json, created_at
            FROM move_events
            WHERE game_id = ?
            ORDER BY id ASC
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, gameId)
            ps.executeQuery().use { rs ->
                val rows = mutableListOf<StoredMoveEvent>()
                while (rs.next()) {
                    rows += StoredMoveEvent(
                        id = rs.getLong("id"),
                        gameId = rs.getString("game_id"),
                        actorPlayerId = rs.getString("actor_player_id"),
                        payloadJson = rs.getString("payload_json"),
                        createdAt = Instant.parse(rs.getString("created_at")),
                    )
                }
                return rows
            }
        }
    }

    private fun java.sql.ResultSet.toStoredGame(): StoredGame {
        return StoredGame(
            gameId = getString("game_id"),
            hostPlayerId = getString("host_player_id"),
            guestPlayerId = getString("guest_player_id"),
            stateJson = getString("state_json"),
            version = getInt("version"),
            createdAt = Instant.parse(getString("created_at")),
            updatedAt = Instant.parse(getString("updated_at")),
        )
    }
}
