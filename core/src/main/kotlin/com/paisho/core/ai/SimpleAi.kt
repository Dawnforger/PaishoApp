package com.paisho.core.ai

import com.paisho.core.game.GameState
import com.paisho.core.game.Move
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.Rules
import kotlin.math.abs
import kotlin.random.Random

class SimpleAi(
    private val random: Random = Random.Default,
) {
    fun chooseMove(state: GameState): Move? {
        if (state.currentPlayer != Player.AI || state.winner != null) return null
        val legalMoves = Rules.legalMoves(state)
        if (legalMoves.isEmpty()) return null

        val scored = legalMoves.map { move -> move to scoreMove(state, move) }
        val topScore = scored.maxOf { (_, score) -> score }
        val topMoves = scored.filter { (_, score) -> score == topScore }.map { (move, _) -> move }
        return topMoves.random(random)
    }

    private fun scoreMove(state: GameState, move: Move): Int {
        var score = 0
        when (move) {
            is Move.Plant -> score += placementCentrality(move.target)
            is Move.Slide -> score += placementCentrality(move.target) + 2
        }

        val next = Rules.applyMove(state, move)
        val aiCount = next.flowers.count { it.owner == Player.AI }
        val humanCount = next.flowers.count { it.owner == Player.HUMAN }
        val aiHarmony = Rules.computeHarmonies(next).count { it.owner == Player.AI }
        val humanHarmony = Rules.computeHarmonies(next).count { it.owner == Player.HUMAN }
        score += aiCount * 3
        score -= humanCount * 2
        score += aiHarmony * 4
        score -= humanHarmony * 3
        if (Rules.hasHarmonyRing(next, Player.AI)) score += 20_000
        if (next.winner == Player.AI) score += 10_000
        return score
    }

    private fun placementCentrality(position: Position): Int {
        return 10 - (abs(position.row) + abs(position.col))
    }
}
