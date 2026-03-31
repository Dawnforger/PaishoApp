package com.paisho.core.game

import com.paisho.core.ai.SimpleAi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {

    @Test
    fun `initial game has opening flowers and plant options`() {
        val state = GameState.initial()
        assertEquals(2, state.flowers.size)
        assertEquals(Player.HUMAN, state.currentPlayer)
        assertEquals(TileType.ROSE, state.flowers.first { it.owner == Player.HUMAN }.type)
        assertEquals(TileType.ROSE, state.flowers.first { it.owner == Player.AI }.type)
        assertEquals(Position(-8, 0), state.flowers.first { it.owner == Player.HUMAN }.position)
        assertEquals(Position(8, 0), state.flowers.first { it.owner == Player.AI }.position)

        val plantMoves = Rules.legalMoves(state).filterIsInstance<Move.Plant>()
        assertTrue(plantMoves.isNotEmpty())
        assertTrue(plantMoves.all { it.target in state.rules.gates })
    }

    @Test
    fun `basic plant consumes reserve and alternates turn`() {
        val state = GameState.initial()
        val beforeBasicRose = state.reserveFor(Player.HUMAN).basicCount(TileType.ROSE)
        val move = Rules.legalMoves(state)
            .filterIsInstance<Move.Plant>()
            .first { it.type == TileType.ROSE }
        val after = Rules.applyMove(state, move)

        assertEquals(Player.AI, after.currentPlayer)
        assertEquals(beforeBasicRose - 1, after.reserveFor(Player.HUMAN).basicCount(TileType.ROSE))
        assertEquals(state.flowers.size + 1, after.flowers.size)
    }

    @Test
    fun `harmony detection works for adjacent circle types`() {
        val state = GameState.initial().copy(
            flowers = listOf(
                FlowerTile(1, Player.HUMAN, TileType.ROSE, Position(0, -2)),
                FlowerTile(2, Player.HUMAN, TileType.CHRYSANTHEMUM, Position(0, 2)),
            ),
            accents = emptyList(),
        )
        val harmonies = Rules.computeHarmonies(state)
        assertEquals(1, harmonies.size)
        assertEquals(Player.HUMAN, harmonies.first().owner)
    }

    @Test
    fun `tile can be trapped by opponent orchid`() {
        val state = GameState.initial().copy(
            currentPlayer = Player.HUMAN,
            flowers = listOf(
                FlowerTile(1, Player.HUMAN, TileType.CHRYSANTHEMUM, Position(0, 0)),
                FlowerTile(2, Player.AI, TileType.ORCHID, Position(1, 1)),
            ),
        )
        val legalMoves = Rules.legalMovesFrom(state, Position(0, 0))
        assertTrue(legalMoves.isEmpty())
    }

    @Test
    fun `harmony ring can be detected`() {
        val state = GameState.initial().copy(
            flowers = listOf(
                FlowerTile(1, Player.HUMAN, TileType.ROSE, Position(3, -3)),
                FlowerTile(2, Player.HUMAN, TileType.CHRYSANTHEMUM, Position(3, 0)),
                FlowerTile(3, Player.HUMAN, TileType.RHODODENDRON, Position(3, 3)),
                FlowerTile(4, Player.HUMAN, TileType.JASMINE, Position(-3, 3)),
                FlowerTile(5, Player.HUMAN, TileType.LILY, Position(-3, 0)),
                FlowerTile(6, Player.HUMAN, TileType.WHITE_JADE, Position(-3, -3)),
            ),
            accents = emptyList(),
        )
        assertTrue(Rules.hasHarmonyRing(state, Player.HUMAN))
    }

    @Test
    fun `last basic tile played ends game by midline crossing count`() {
        val emptyHuman = stateWithNoBasic(Player.HUMAN)
        val emptyAi = stateWithNoBasic(Player.AI)
        val humanReserve = emptyHuman.copy(
            basic = emptyHuman.basic + (TileType.ROSE to 1)
        )
        val aiReserve = emptyAi
        val state = GameState.initial().copy(
            currentPlayer = Player.HUMAN,
            reserves = mapOf(
                Player.HUMAN to humanReserve,
                Player.AI to aiReserve,
            ),
        )
        val plant = Move.Plant(TileType.ROSE, state.rules.gates.first { !state.isOccupied(it) })
        val after = Rules.applyMove(state, plant)
        assertEquals(GamePhase.FINISHED, after.phase)
        assertEquals(GameEndReason.LAST_BASIC_PLAYED, after.endReason)
        assertNull(after.winner)
        assertTrue(after.isDraw)
    }

    @Test
    fun `ai chooses legal move`() {
        val state = GameState.initial().copy(currentPlayer = Player.AI)
        val ai = SimpleAi(Random(0))
        val move = ai.chooseMove(state)
        assertNotNull(move)
        assertTrue(Rules.legalMoves(state).contains(move))
    }

    @Test
    fun `ai expands board presence when planting options exist`() {
        val state = GameState.initial().copy(
            currentPlayer = Player.AI,
            flowers = listOf(
                FlowerTile(1, Player.HUMAN, TileType.ROSE, Position(-8, 0)),
                FlowerTile(2, Player.AI, TileType.ROSE, Position(8, 0)),
                // Trap the AI rose so slide options disappear and only plant moves remain.
                FlowerTile(3, Player.HUMAN, TileType.ORCHID, Position(7, 1)),
            ),
            nextFlowerId = 4,
        )

        val legalSlides = Rules.legalMoves(state).filterIsInstance<Move.Slide>()
        assertTrue(legalSlides.isEmpty())

        val move = SimpleAi(Random(0)).chooseMove(state)
        assertTrue(move is Move.Plant)
    }

    @Test
    fun `harmony bonus options include only legal reserve-driven actions`() {
        val base = GameState.initial().copy(
            currentPlayer = Player.HUMAN,
            flowers = listOf(
                // Sliding tile 1 from (0,-2) to (0,-1) creates a new harmony with tile 2 at (0,1).
                FlowerTile(1, Player.HUMAN, TileType.ROSE, Position(0, -2)),
                FlowerTile(2, Player.HUMAN, TileType.CHRYSANTHEMUM, Position(0, 1)),
                FlowerTile(3, Player.AI, TileType.JASMINE, Position(3, 0)),
            ),
            accents = emptyList(),
            reserves = mapOf(
                Player.HUMAN to GameState.initial().reserveFor(Player.HUMAN),
                Player.AI to GameState.initial().reserveFor(Player.AI),
            ),
        )

        val harmonySlides = Rules.legalMoves(base)
            .filterIsInstance<Move.Slide>()
            .filter { it.tileId == 1 && it.target == Position(0, -1) }

        assertTrue(harmonySlides.any { it.bonus != null })
        // Arrange-without-bonus is still legal; reserve-driven bonus selection is a UI flow choice.
        assertTrue(harmonySlides.any { it.bonus == null })
    }

    private fun stateWithNoBasic(player: Player): PlayerReserve {
        return GameState.initial().reserveFor(player).copy(
            basic = TileType.basicTypes.associateWith { 0 }
        )
    }
}
