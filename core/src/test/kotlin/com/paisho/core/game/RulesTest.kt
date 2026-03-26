package com.paisho.core.game

import com.paisho.core.ai.SimpleAi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RulesTest {

    @Test
    fun `planting moves are edge-only plants`() {
        val state = GameState.initial()
        val moves = Rules.legalMoves(state)
        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { it is Move.Plant })
        moves.forEach { move ->
            val plant = move as Move.Plant
            assertTrue(plant.target.isEdge(state.rules.boardSize))
        }
    }

    @Test
    fun `apply plant alternates turn and increments count`() {
        val state = GameState.initial()
        val move = Rules.legalMoves(state).first() as Move.Plant
        val after = Rules.applyMove(state, move)

        assertEquals(Player.AI, after.currentPlayer)
        assertEquals(1, after.tiles.count { it.owner == Player.HUMAN })
        assertEquals(1, after.plantsUsedHuman)
    }

    @Test
    fun `transitions from planting to movement after opening turns`() {
        var state = GameState.initial()
        repeat(4) {
            val move = Rules.legalMoves(state).first()
            state = Rules.applyMove(state, move)
        }
        assertEquals(GamePhase.MOVEMENT, state.phase)
    }

    @Test
    fun `slide captures opposing tile`() {
        val state = GameState(
            phase = GamePhase.MOVEMENT,
            currentPlayer = Player.HUMAN,
            tiles = listOf(
                Tile(1, Player.HUMAN, TileType.LILY, Position(4, 1)),
                Tile(2, Player.AI, TileType.ROSE, Position(4, 3)),
            ),
            nextTileId = 3,
        )
        val move = Move.Slide(tileId = 1, target = Position(4, 3))
        val after = Rules.applyMove(state, move)
        assertEquals(1, after.tiles.count { it.owner == Player.HUMAN })
        assertEquals(0, after.tiles.count { it.owner == Player.AI })
        assertEquals(Player.HUMAN, after.winner)
    }

    @Test
    fun `ai chooses legal move`() {
        val state = GameState(
            currentPlayer = Player.AI,
            phase = GamePhase.PLANTING,
        )
        val ai = SimpleAi(Random(0))
        val move = ai.chooseMove(state)
        assertNotNull(move)
        assertTrue(Rules.legalMoves(state).contains(move))
    }
}
