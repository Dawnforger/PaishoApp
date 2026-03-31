package com.paisho.core.ai

import com.paisho.core.game.AccentType
import com.paisho.core.game.BonusAction
import com.paisho.core.game.GameState
import com.paisho.core.game.Move
import com.paisho.core.game.Player
import com.paisho.core.game.Position
import com.paisho.core.game.Rules
import com.paisho.core.game.TileType
import kotlin.math.abs
import kotlin.random.Random

class SimpleAi(
    private val random: Random = Random.Default,
) {
    private val priors = LearnedMovePriors.default()
    private val maxFullEvaluations = 72
    private val maxOpponentThreatChecks = 36

    fun chooseMove(state: GameState): Move? {
        if (state.currentPlayer != Player.AI || state.winner != null) return null
        val legalMoves = Rules.legalMoves(state)
        if (legalMoves.isEmpty()) return null

        val immediateWins = legalMoves.filter { move ->
            val after = Rules.applyMove(state, move)
            after.winner == Player.AI || Rules.hasHarmonyRing(after, Player.AI)
        }
        if (immediateWins.isNotEmpty()) {
            return immediateWins.random(random)
        }

        // Keep search bounded in large branching states to avoid turn-time stalls.
        val candidates = if (legalMoves.size > maxFullEvaluations) {
            legalMoves
                .map { move -> move to quickScore(state, move) }
                .sortedByDescending { it.second }
                .take(maxFullEvaluations)
                .map { it.first }
        } else {
            legalMoves
        }

        val scored = candidates.map { move -> move to scoreMove(state, move) }
        val topScore = scored.maxOf { (_, score) -> score }
        val topMoves = scored.filter { (_, score) -> score == topScore }.map { (move, _) -> move }
        return topMoves.random(random)
    }

    private fun quickScore(state: GameState, move: Move): Double {
        var score = aggressiveMoveBias(state, move)
        when (move) {
            is Move.Plant -> score += placementCentrality(move.target) * 0.5
            is Move.Slide -> score += placementCentrality(move.target).toDouble() + 2.0
        }
        score += policyPriorScore(state, move)
        return score
    }

    private fun scoreMove(state: GameState, move: Move): Double {
        var score = quickScore(state, move)

        val next = Rules.applyMove(state, move)
        score += strategicStateScore(next)
        if (opponentHasImmediateWin(next)) score -= 250_000.0
        if (next.winner == Player.AI) score += 1_000_000.0
        if (next.winner == Player.HUMAN) score -= 1_000_000.0
        return score
    }

    private fun placementCentrality(position: Position): Int {
        return 10 - (abs(position.row) + abs(position.col))
    }

    private fun strategicStateScore(state: GameState): Double {
        val aiFlowers = state.flowersFor(Player.AI)
        val humanFlowers = state.flowersFor(Player.HUMAN)
        val aiBlooming = aiFlowers.count { !state.isGate(it.position) }
        val humanBlooming = humanFlowers.count { !state.isGate(it.position) }
        val aiOnGate = aiFlowers.size - aiBlooming
        val humanOnGate = humanFlowers.size - humanBlooming

        val harmonies = Rules.computeHarmonies(state)
        val aiHarmony = harmonies.count { it.owner == Player.AI }
        val humanHarmony = harmonies.count { it.owner == Player.HUMAN }
        val aiMidline = Rules.computeMidlineCrossingHarmonyCount(state, Player.AI)
        val humanMidline = Rules.computeMidlineCrossingHarmonyCount(state, Player.HUMAN)

        val aiMobility = Rules.legalMoves(state.copy(currentPlayer = Player.AI)).size
        val humanMobility = Rules.legalMoves(state.copy(currentPlayer = Player.HUMAN)).size

        var score = 0.0
        score += (aiFlowers.size - humanFlowers.size) * 120.0
        score += (aiBlooming - humanBlooming) * 160.0
        score += aiHarmony * 320.0
        score -= humanHarmony * 360.0
        score += (aiMidline - humanMidline) * 120.0
        score += (aiMobility - humanMobility) * 2.0
        score -= aiOnGate * 35.0
        score += humanOnGate * 25.0
        if (Rules.hasHarmonyRing(state, Player.AI)) score += 300_000.0
        if (Rules.hasHarmonyRing(state, Player.HUMAN)) score -= 300_000.0
        return score
    }

    private fun aggressiveMoveBias(state: GameState, move: Move): Double {
        val aiFlowers = state.flowersFor(Player.AI)
        val aiBlooming = aiFlowers.count { !state.isGate(it.position) }

        return when (move) {
            is Move.Plant -> {
                var score = 60.0
                if (aiFlowers.size < 4) score += 180.0
                if (aiBlooming < 3) score += 100.0
                score
            }
            is Move.Slide -> {
                var score = 20.0
                val mover = state.flowers.firstOrNull { it.id == move.tileId }
                if (mover != null && state.isGate(mover.position) && aiFlowers.size < 4) {
                    // Early game: avoid repeatedly shuffling one opening tile from a gate.
                    score -= 140.0
                }
                val target = state.flowerAt(move.target)
                if (target != null && target.owner == Player.HUMAN) score += 220.0
                if (move.bonus != null) score += 60.0
                score
            }
        }
    }

    private fun opponentHasImmediateWin(stateAfterAiMove: GameState): Boolean {
        if (stateAfterAiMove.phase != com.paisho.core.game.GamePhase.PLAYING) return false
        val opponentMoves = Rules.legalMoves(stateAfterAiMove)
        if (opponentMoves.isEmpty()) return false
        val candidateReplies = if (opponentMoves.size > maxOpponentThreatChecks) {
            opponentMoves
                .asSequence()
                .sortedByDescending { replyThreatBias(stateAfterAiMove, it) }
                .take(maxOpponentThreatChecks)
                .toList()
        } else {
            opponentMoves
        }
        return candidateReplies.any { reply ->
            val afterReply = Rules.applyMove(stateAfterAiMove, reply)
            afterReply.winner == Player.HUMAN || Rules.hasHarmonyRing(afterReply, Player.HUMAN)
        }
    }

    private fun replyThreatBias(state: GameState, move: Move): Int {
        return when (move) {
            is Move.Plant -> {
                // Basic plants are the most frequent way to rapidly increase crossing-harmony pressure.
                10 + if (move.type.isBasic) 4 else 0
            }
            is Move.Slide -> {
                var score = 8
                if (move.bonus != null) score += 5
                val target = state.flowerAt(move.target)
                if (target != null && target.owner == Player.AI) score += 7
                score + placementCentrality(move.target)
            }
        }
    }

    private fun policyPriorScore(state: GameState, move: Move): Double {
        return when (move) {
            is Move.Plant -> {
                val openingWeight = if (state.turnNumber <= 4) 1.0 else 0.0
                val openingPlant = priors.openingPlantCodeLog[move.type] ?: priors.defaultPlantLog
                val openingGate = priors.openingGateLog[move.target] ?: priors.defaultGateLog
                val generalPlant = priors.plantCodeLog[move.type] ?: priors.defaultPlantLog
                val generalGate = priors.gateLog[move.target] ?: priors.defaultGateLog
                val openingPlantDelta = openingPlant - priors.defaultPlantLog
                val openingGateDelta = openingGate - priors.defaultGateLog
                val generalPlantDelta = generalPlant - priors.defaultPlantLog
                val generalGateDelta = generalGate - priors.defaultGateLog
                (openingWeight * openingPlantDelta) +
                    (openingWeight * openingGateDelta) +
                    (0.7 * generalPlantDelta) +
                    (0.9 * generalGateDelta)
            }
            is Move.Slide -> {
                val source = state.flowers.firstOrNull { it.id == move.tileId }?.position
                val delta = source?.let { Position(move.target.row - it.row, move.target.col - it.col) }
                val deltaLog = if (delta != null) {
                    priors.slideDeltaLog[delta] ?: priors.defaultSlideDeltaLog
                } else {
                    priors.defaultSlideDeltaLog
                }
                val bonusLog = priors.bonusKindLog[bonusKind(move.bonus)] ?: priors.defaultBonusLog
                val deltaPrior = deltaLog - priors.defaultSlideDeltaLog
                val bonusPrior = bonusLog - priors.defaultBonusLog
                (0.5 * deltaPrior) + (0.35 * bonusPrior)
            }
        }
    }

    private fun bonusKind(bonus: BonusAction?): String {
        return when (bonus) {
            null -> "none"
            is BonusAction.BoatMove -> "boat_move"
            is BonusAction.BoatRemoveAccent -> "boat_remove"
            is BonusAction.PlantBonus -> if (bonus.tileType.isBasic) "plant_basic" else "plant_special"
            is BonusAction.PlaceAccent -> when (bonus.type) {
                AccentType.ROCK -> "accent_R"
                AccentType.WHEEL -> "accent_W"
                AccentType.KNOTWEED -> "accent_K"
                AccentType.BOAT -> "unknown"
            }
        }
    }
}

private data class LearnedMovePriors(
    val openingPlantCodeLog: Map<TileType, Double>,
    val openingGateLog: Map<Position, Double>,
    val plantCodeLog: Map<TileType, Double>,
    val gateLog: Map<Position, Double>,
    val slideDeltaLog: Map<Position, Double>,
    val bonusKindLog: Map<String, Double>,
    val defaultPlantLog: Double,
    val defaultGateLog: Double,
    val defaultSlideDeltaLog: Double,
    val defaultBonusLog: Double,
) {
    companion object {
        fun default(): LearnedMovePriors {
            return LearnedMovePriors(
                openingPlantCodeLog = mapOf(
                    TileType.ROSE to -1.5569067387880038,
                    TileType.CHRYSANTHEMUM to -2.061224375891799,
                    TileType.RHODODENDRON to -1.5086076131749642,
                    TileType.JASMINE to -2.1400444331052317,
                    TileType.LILY to -2.6345042787994046,
                    TileType.WHITE_JADE to -1.3830400680085664,
                    TileType.WHITE_LOTUS to -9.350493542159587,
                    TileType.ORCHID to -8.945028434051423,
                ),
                openingGateLog = mapOf(
                    Position(0, -8) to -1.2364979056223926,
                    Position(0, 8) to -1.318058498501823,
                    Position(8, 0) to -1.4936690537906436,
                    Position(-8, 0) to -1.5467613393990831,
                ),
                plantCodeLog = mapOf(
                    TileType.ROSE to -1.615974399385397,
                    TileType.CHRYSANTHEMUM to -2.010490636745548,
                    TileType.RHODODENDRON to -1.58049533979181,
                    TileType.JASMINE to -2.0354770645282785,
                    TileType.LILY to -2.375086747912824,
                    TileType.WHITE_JADE to -1.4380979999347203,
                    TileType.WHITE_LOTUS to -8.756902765318923,
                    TileType.ORCHID to -9.267728389084914,
                ),
                gateLog = mapOf(
                    Position(0, -8) to -1.2692268515365652,
                    Position(0, 8) to -1.355283884666642,
                    Position(8, 0) to -1.4573754082334338,
                    Position(-8, 0) to -1.5054443221146119,
                ),
                slideDeltaLog = mapOf(
                    Position(-1, -1) to -3.7028007085702472,
                    Position(-1, -2) to -3.589771750253297,
                    Position(-1, -3) to -4.393397332090738,
                    Position(-1, 0) to -3.9937716566341335,
                    Position(-1, 1) to -3.57361108826523,
                    Position(-1, 2) to -3.362790671058124,
                    Position(-1, 3) to -4.376796445616072,
                    Position(-2, -1) to -3.6659120139499017,
                    Position(-2, -2) to -4.4673933409682975,
                    Position(-2, 0) to -4.011497379233242,
                    Position(-2, 1) to -3.5184139138436707,
                    Position(-2, 2) to -4.327904026730051,
                    Position(-3, 0) to -3.787261528520423,
                    Position(-4, 0) to -4.468177347280506,
                    Position(-4, 1) to -4.46426344795937,
                    Position(-5, 0) to -3.937779803336674,
                    Position(0, -1) to -4.133387196842278,
                    Position(0, -2) to -4.094317578587673,
                    Position(0, -3) to -3.668728917221015,
                    Position(0, -5) to -4.178732124210345,
                    Position(0, 1) to -4.000136427202619,
                    Position(0, 2) to -3.9122658909088366,
                    Position(0, 3) to -3.5349331025774116,
                    Position(0, 5) to -4.009016603959479,
                    Position(1, -1) to -3.687234783418723,
                    Position(1, -2) to -3.476815521768887,
                    Position(1, -3) to -4.491987995974225,
                    Position(1, 0) to -4.08679066133131,
                    Position(1, 1) to -3.6596028447566367,
                    Position(1, 2) to -3.561184610057446,
                    Position(1, 3) to -4.354848661973503,
                    Position(1, 4) to -4.502484660779568,
                    Position(2, -1) to -3.6568114961193663,
                    Position(2, 0) to -4.05984054942965,
                    Position(2, 1) to -3.60223092781523,
                    Position(2, 2) to -4.408060351400813,
                    Position(3, 0) to -3.7548431458005824,
                    Position(3, 1) to -4.393397332090738,
                    Position(4, 0) to -4.480805770828686,
                    Position(5, 0) to -3.9884175430894064,
                ),
                bonusKindLog = mapOf(
                    "none" to -0.42364418527685405,
                    "plant_basic" to -1.702850097495457,
                    "plant_special" to -2.8032694187012686,
                    "accent_W" to -3.7012005168005704,
                    "accent_R" to -3.7308216615917753,
                    "accent_K" to -3.7372638507825755,
                    "boat_move" to -4.206562675582361,
                    "boat_remove" to -4.252702526847494,
                    "unknown" to -6.949989414647716,
                ),
                defaultPlantLog = -9.267728389084914,
                defaultGateLog = -9.675488482245521,
                defaultSlideDeltaLog = -4.502484660779568,
                defaultBonusLog = -6.949989414647716,
            )
        }
    }
}
