package com.paisho.core.game

import kotlin.math.abs

object Rules {
    private val cardinalDirections = listOf(
        Position(1, 0),
        Position(-1, 0),
        Position(0, 1),
        Position(0, -1),
    )

    private val clockwise8 = listOf(
        Position(-1, 0),
        Position(-1, 1),
        Position(0, 1),
        Position(1, 1),
        Position(1, 0),
        Position(1, -1),
        Position(0, -1),
        Position(-1, -1),
    )

    fun legalMoves(state: GameState): List<Move> {
        if (state.phase == GamePhase.FINISHED || state.winner != null || state.isDraw) return emptyList()
        return legalPlants(state) + legalSlides(state)
    }

    fun legalMovesFrom(state: GameState, from: Position): List<Move.Slide> {
        val tile = state.flowerAt(from) ?: return emptyList()
        if (tile.owner != state.currentPlayer) return emptyList()
        return legalSlidesForTile(state, tile)
    }

    fun computeHarmonies(state: GameState): List<Harmony> {
        val harmonies = mutableListOf<Harmony>()
        val blooming = state.flowers.filter { !state.isGate(it.position) }

        for (i in blooming.indices) {
            for (j in (i + 1) until blooming.size) {
                val a = blooming[i]
                val b = blooming[j]
                if (a.position.row != b.position.row && a.position.col != b.position.col) continue
                if (hasBlockingPoint(state, a.position, b.position)) continue
                if (!flowersCanHarmonize(a, b)) continue
                if (isHarmonyCancelledByRock(state, a.position, b.position)) continue
                if (isHarmonyCancelledByKnotweed(state, a.position, b.position)) continue
                harmonies += Harmony(
                    owner = harmonyOwner(a, b),
                    a = a.position,
                    b = b.position,
                )
            }
        }
        return harmonies
    }

    fun computeMidlineCrossingHarmonyCount(state: GameState, player: Player): Int {
        val center = 0
        return computeHarmonies(state).count { harmony ->
            if (harmony.owner != player) return@count false
            if (harmony.a.row == center || harmony.a.col == center) return@count false
            if (harmony.b.row == center || harmony.b.col == center) return@count false
            val quadrantsDiffer = (harmony.a.row < center) != (harmony.b.row < center) ||
                (harmony.a.col < center) != (harmony.b.col < center)
            quadrantsDiffer && !segmentPassesThroughCenter(state, harmony.a, harmony.b)
        }
    }

    fun hasHarmonyRing(state: GameState, player: Player): Boolean {
        val center = Position(0, 0)
        val edges = computeHarmonies(state).filter { it.owner == player }
        if (edges.isEmpty()) return false

        val adjacency = mutableMapOf<Position, MutableSet<Position>>()
        edges.forEach { edge ->
            adjacency.getOrPut(edge.a) { mutableSetOf() }.add(edge.b)
            adjacency.getOrPut(edge.b) { mutableSetOf() }.add(edge.a)
        }

        val exploredStartEdges = mutableSetOf<Pair<Position, Position>>()
        for ((start, neighbors) in adjacency) {
            for (next in neighbors) {
                val edgeKey = normalizedEdge(start, next)
                if (edgeKey in exploredStartEdges) continue
                val localUsed = mutableSetOf(edgeKey)
                if (dfsRing(state, adjacency, start, next, start, localUsed, center)) {
                    return true
                }
                exploredStartEdges += localUsed
            }
        }
        return false
    }

    fun applyMove(state: GameState, move: Move): GameState {
        require(move in legalMoves(state)) { "Illegal move: $move" }
        val beforeHarmonies = computeHarmonies(state).map { it.key }.toSet()

        val afterPrimary = when (move) {
            is Move.Plant -> applyPlant(state, move)
            is Move.Slide -> applySlide(state, move)
        }

        val formedNewHarmony = when (move) {
            is Move.Slide -> {
                val afterKeys = computeHarmonies(afterPrimary)
                    .filter { it.owner == state.currentPlayer }
                    .map { it.key }
                    .toSet()
                afterKeys.any { it !in beforeHarmonies }
            }
            is Move.Plant -> false
        }

        val afterBonus = when (move) {
            is Move.Slide -> applyBonusIfPresent(afterPrimary, move.bonus, formedNewHarmony)
            is Move.Plant -> afterPrimary
        }

        val beforeCurrentBasicCount = state.reserveFor(state.currentPlayer).totalBasicCount()
        val afterCurrentBasicCount = afterBonus.reserveFor(state.currentPlayer).totalBasicCount()
        val consumedBasic = when (move) {
            is Move.Plant -> move.type.isBasic
            is Move.Slide -> move.bonus is BonusAction.PlantBonus && move.bonus.tileType.isBasic
        }
        val playedLastBasicThisTurn = consumedBasic &&
            beforeCurrentBasicCount > 0 &&
            afterCurrentBasicCount == 0

        val withEnd = evaluateEndConditions(afterBonus, playedLastBasicThisTurn)
        return withEnd.copy(
            currentPlayer = if (withEnd.phase == GamePhase.FINISHED) withEnd.currentPlayer else withEnd.currentPlayer.other(),
            turnNumber = withEnd.turnNumber + 1,
        )
    }

    private fun legalPlants(state: GameState): List<Move.Plant> {
        val reserve = state.reserves.getValue(state.currentPlayer)
        val moves = mutableListOf<Move.Plant>()
        state.rules.gates.forEach { gate ->
            if (state.isOccupied(gate)) return@forEach
            TileType.basicTypes.forEach { type ->
                if (reserve.basicCount(type) == 0) return@forEach
                val move = Move.Plant(type, gate)
                if (isMoveLegalBySimulation(state, move)) moves += move
            }
        }
        return moves
    }

    private fun legalSlides(state: GameState): List<Move.Slide> {
        val basicSlides = state.flowersFor(state.currentPlayer).flatMap { tile ->
            legalSlidesForTile(state, tile)
        }
        return basicSlides.flatMap { slide ->
            val primary = applySlide(state, slide)
            val formedNewHarmony = computeHarmonies(primary)
                .filter { it.owner == state.currentPlayer }
                .map { it.key }
                .toSet() - computeHarmonies(state)
                .filter { it.owner == state.currentPlayer }
                .map { it.key }
                .toSet()
            if (formedNewHarmony.isEmpty()) return@flatMap listOf(slide)

            val bonusMoves = legalBonusActions(primary).map { bonus -> slide.copy(bonus = bonus) }
            listOf(slide) + bonusMoves
        }
    }

    private fun legalSlidesForTile(state: GameState, tile: FlowerTile): List<Move.Slide> {
        if (tile.owner != state.currentPlayer) return emptyList()
        if (isTileTrappedByOpponentOrchid(state, tile)) return emptyList()

        val slides = mutableListOf<Move.Slide>()
        cardinalDirections.forEach { dir ->
            explorePaths(
                state = state,
                tile = tile,
                origin = tile.position,
                current = tile.position,
                remaining = tile.type.maxMoveDistance,
                dir = dir,
                results = slides,
                visited = mutableSetOf(tile.position),
            )
        }
        return slides.distinctBy { it.target }
    }

    private fun explorePaths(
        state: GameState,
        tile: FlowerTile,
        origin: Position,
        current: Position,
        remaining: Int,
        dir: Position,
        results: MutableList<Move.Slide>,
        visited: MutableSet<Position>,
    ) {
        if (remaining == 0) return
        val next = Position(current.row + dir.row, current.col + dir.col)
        if (!state.isOnBoard(next)) return
        if (state.accentAt(next) != null) return

        val occupant = state.flowerAt(next)
        if (occupant != null && occupant.owner == tile.owner) return
        if (occupant != null && !canCapture(state, tile, occupant)) return
        if (occupant == null && !canEndMoveOn(state, tile, next)) {
            // path may continue through this point, if empty
        }

        if (occupant == null) {
            if (canEndMoveOn(state, tile, next)) {
                val slide = Move.Slide(tile.id, next, null)
                if (isMoveLegalBySimulation(state, slide)) results += slide
            }
            if (next !in visited) {
                visited += next
                cardinalDirections.forEach { nextDir ->
                    explorePaths(state, tile, origin, next, remaining - 1, nextDir, results, visited)
                }
                visited -= next
            }
            return
        }

        // capturing ends movement
        val captureMove = Move.Slide(tile.id, next, null)
        if (canEndMoveOn(state, tile, next) && isMoveLegalBySimulation(state, captureMove)) {
            results += captureMove
        }
    }

    private fun legalBonusActions(stateAfterSlide: GameState): List<BonusAction> {
        val player = stateAfterSlide.currentPlayer
        val reserve = stateAfterSlide.reserves.getValue(player)
        val actions = mutableListOf<BonusAction>()

        // Accent placements and accent-specific actions
        actions += legalAccentActions(stateAfterSlide)

        // Special flower plant bonus
        stateAfterSlide.rules.gates.forEach { gate ->
            if (stateAfterSlide.isOccupied(gate)) return@forEach
            TileType.specialTypes.forEach { special ->
                if (reserve.specialCount(special) == 0) return@forEach
                val action = BonusAction.PlantBonus(special, gate)
                if (isBonusLegalBySimulation(stateAfterSlide, action)) actions += action
            }
        }

        // Basic flower bonus if no growing flowers
        if (!stateAfterSlide.hasGrowingFlower(player)) {
            stateAfterSlide.rules.gates.forEach { gate ->
                if (stateAfterSlide.isOccupied(gate)) return@forEach
                TileType.basicTypes.forEach { type ->
                    if (reserve.basicCount(type) == 0) return@forEach
                    val action = BonusAction.PlantBonus(type, gate)
                    if (isBonusLegalBySimulation(stateAfterSlide, action)) actions += action
                }
            }
        }

        return actions
    }

    private fun legalAccentActions(state: GameState): List<BonusAction> {
        val player = state.currentPlayer
        val reserve = state.reserves.getValue(player)
        val actions = mutableListOf<BonusAction>()

        if (reserve.accentCount(AccentType.ROCK) > 0) {
            allBoardPositions(state).forEach { pos ->
                val action = BonusAction.PlaceAccent(AccentType.ROCK, pos)
                if (isBonusLegalBySimulation(state, action)) actions += action
            }
        }

        if (reserve.accentCount(AccentType.KNOTWEED) > 0) {
            allBoardPositions(state).forEach { pos ->
                val action = BonusAction.PlaceAccent(AccentType.KNOTWEED, pos)
                if (isBonusLegalBySimulation(state, action)) actions += action
            }
        }

        if (reserve.accentCount(AccentType.WHEEL) > 0) {
            allBoardPositions(state).forEach { pos ->
                val action = BonusAction.PlaceAccent(AccentType.WHEEL, pos)
                if (isBonusLegalBySimulation(state, action)) actions += action
            }
        }

        if (reserve.accentCount(AccentType.BOAT) > 0) {
            state.flowers
                .filter { !state.isGate(it.position) }
                .forEach { flower ->
                    flower.position.surrounding8().filter { state.isOnBoard(it) }.forEach { dest ->
                        val action = BonusAction.BoatMove(flower.position, dest)
                        if (isBonusLegalBySimulation(state, action)) actions += action
                    }
                }
            state.accents.filter { it.type != AccentType.BOAT }.forEach { accent ->
                val action = BonusAction.BoatRemoveAccent(accent.position)
                if (isBonusLegalBySimulation(state, action)) actions += action
            }
        }

        return actions
    }

    fun legalBonusActionsForCurrentPlayer(stateAfterSlide: GameState): List<BonusAction> {
        require(stateAfterSlide.phase == GamePhase.PLAYING) { "Cannot query bonus actions for finished games." }
        return legalBonusActions(stateAfterSlide)
    }

    private fun allBoardPositions(state: GameState): List<Position> = state.rules.legalPositions.toList()

    private fun applyPlant(state: GameState, move: Move.Plant): GameState {
        val reserve = state.reserves.getValue(state.currentPlayer)
        val nextReserve = if (move.type.isBasic) reserve.spendBasic(move.type) else reserve.spendSpecial(move.type)
        val flower = FlowerTile(
            id = state.nextFlowerId,
            owner = state.currentPlayer,
            type = move.type,
            position = move.target,
        )
        return state.copy(
            flowers = state.flowers + flower,
            reserves = state.reserves + (state.currentPlayer to nextReserve),
            nextFlowerId = state.nextFlowerId + 1,
        )
    }

    private fun applySlide(state: GameState, move: Move.Slide): GameState {
        val mover = state.flowers.firstOrNull { it.id == move.tileId && it.owner == state.currentPlayer }
            ?: error("Missing mover for $move")

        val captured = state.flowerAt(move.target)
        val remainingFlowers = state.flowers.filterNot { it.id == mover.id || it.id == captured?.id }
        val moved = mover.copy(position = move.target)
        return state.copy(flowers = remainingFlowers + moved)
    }

    private fun applyBonusIfPresent(state: GameState, bonus: BonusAction?, formedNewHarmony: Boolean): GameState {
        if (bonus == null) return state
        require(formedNewHarmony) { "Bonus action requires creating a new Harmony with Arrange." }
        return applyBonusAction(state, bonus)
    }

    private fun applyBonusAction(state: GameState, bonus: BonusAction): GameState {
        val player = state.currentPlayer
        val reserve = state.reserves.getValue(player)
        return when (bonus) {
            is BonusAction.PlaceAccent -> {
                when (bonus.type) {
                    AccentType.ROCK, AccentType.KNOTWEED -> {
                        val accent = AccentTile(
                            id = state.nextAccentId,
                            owner = player,
                            type = bonus.type,
                            position = bonus.target,
                        )
                        state.copy(
                            accents = state.accents + accent,
                            reserves = state.reserves + (player to reserve.spendAccent(bonus.type)),
                            nextAccentId = state.nextAccentId + 1,
                        )
                    }
                    AccentType.WHEEL -> {
                        val wheelId = state.nextAccentId
                        val withWheel = state.copy(
                            accents = state.accents + AccentTile(wheelId, player, AccentType.WHEEL, bonus.target),
                            reserves = state.reserves + (player to reserve.spendAccent(AccentType.WHEEL)),
                            nextAccentId = wheelId + 1,
                        )
                        rotateAroundWheel(withWheel, bonus.target)
                    }
                    AccentType.BOAT -> error("Boat placement uses Boat bonus actions.")
                }
            }
            is BonusAction.PlantBonus -> {
                val move = Move.Plant(bonus.tileType, bonus.gate)
                applyPlant(state, move)
            }
            is BonusAction.BoatMove -> {
                val boatId = state.nextAccentId
                val sourceFlower = state.flowerAt(bonus.source) ?: error("No flower at boat source")
                val movedFlower = sourceFlower.copy(position = bonus.destination)
                val flowers = state.flowers.filterNot { it.id == sourceFlower.id } + movedFlower
                val accents = state.accents + AccentTile(boatId, player, AccentType.BOAT, bonus.source)
                state.copy(
                    flowers = flowers,
                    accents = accents,
                    reserves = state.reserves + (player to reserve.spendAccent(AccentType.BOAT)),
                    nextAccentId = boatId + 1,
                )
            }
            is BonusAction.BoatRemoveAccent -> {
                val target = state.accentAt(bonus.targetAccent) ?: error("No accent to remove")
                val accents = state.accents.filterNot { it.id == target.id }
                state.copy(
                    accents = accents,
                    reserves = state.reserves + (player to reserve.spendAccent(AccentType.BOAT)),
                )
            }
        }
    }

    private fun rotateAroundWheel(state: GameState, wheelCenter: Position): GameState {
        val wheel = state.accentAt(wheelCenter) ?: return state
        if (wheel.type != AccentType.WHEEL) return state

        val around = clockwise8.map { Position(wheelCenter.row + it.row, wheelCenter.col + it.col) }
        val mapping = around.zip(around.drop(1) + around.take(1)).toMap()

        val flowersByPos = state.flowers.associateBy { it.position }
        val accentsByPos = state.accents.associateBy { it.position }

        val movedFlowers = mutableListOf<FlowerTile>()
        val removedFlowerIds = mutableSetOf<Int>()
        val movedAccents = mutableListOf<AccentTile>()
        val removedAccentIds = mutableSetOf<Int>()

        around.forEach { from ->
            val to = mapping.getValue(from)
            val flower = flowersByPos[from]
            if (flower != null) {
                movedFlowers += flower.copy(position = to)
                removedFlowerIds += flower.id
            }
            val accent = accentsByPos[from]
            if (accent != null && accent.type != AccentType.ROCK) {
                movedAccents += accent.copy(position = to)
                removedAccentIds += accent.id
            }
        }

        return state.copy(
            flowers = state.flowers.filterNot { it.id in removedFlowerIds } + movedFlowers,
            accents = state.accents.filterNot { it.id in removedAccentIds } + movedAccents,
        )
    }

    private fun evaluateEndConditions(state: GameState, playedLastBasicThisTurn: Boolean): GameState {
        val humanRing = hasHarmonyRing(state, Player.HUMAN)
        val aiRing = hasHarmonyRing(state, Player.AI)
        if (humanRing || aiRing) {
            val winner = when {
                humanRing && aiRing -> null
                humanRing -> Player.HUMAN
                else -> Player.AI
            }
            return state.copy(
                phase = GamePhase.FINISHED,
                winner = winner,
                isDraw = winner == null,
                endReason = GameEndReason.HARMONY_RING,
            )
        }

        if (playedLastBasicThisTurn) {
            val humanMid = computeMidlineCrossingHarmonyCount(state, Player.HUMAN)
            val aiMid = computeMidlineCrossingHarmonyCount(state, Player.AI)
            val winner = when {
                humanMid > aiMid -> Player.HUMAN
                aiMid > humanMid -> Player.AI
                else -> null
            }
            return state.copy(
                phase = GamePhase.FINISHED,
                winner = winner,
                isDraw = winner == null,
                endReason = GameEndReason.LAST_BASIC_PLAYED,
            )
        }
        return state
    }

    private fun isMoveLegalBySimulation(state: GameState, move: Move): Boolean {
        return runCatching {
            val after = when (move) {
                is Move.Plant -> applyPlant(state, move)
                is Move.Slide -> applySlide(state, move)
            }
            validateBoardState(after)
        }.isSuccess
    }

    private fun isBonusLegalBySimulation(state: GameState, bonus: BonusAction): Boolean {
        return runCatching {
            val after = applyBonusAction(state, bonus)
            validateBoardState(after)
        }.isSuccess
    }

    private fun validateBoardState(state: GameState) {
        // No overlaps and all on board.
        val flowerPositions = state.flowers.map { it.position }
        val accentPositions = state.accents.map { it.position }
        require(flowerPositions.all { state.isOnBoard(it) } && accentPositions.all { state.isOnBoard(it) })
        require(flowerPositions.toSet().size == flowerPositions.size)
        require(accentPositions.toSet().size == accentPositions.size)
        require((flowerPositions + accentPositions).toSet().size == flowerPositions.size + accentPositions.size)

        // Accent placement constraints.
        state.accents.forEach { accent ->
            require(!state.isGate(accent.position)) { "Accent cannot be on a Gate." }
        }

        // Basic flower garden constraint.
        state.flowers.forEach { flower ->
            if (!flower.type.isBasic) return@forEach
            val garden = state.gardenColor(flower.position)
            if (garden == GardenColor.NEUTRAL) return@forEach
            require(garden == flower.type.basicColor) { "Basic flower ended in opposite-colored Garden." }
        }

        // No clashes on board.
        require(!hasAnyClash(state)) { "Board contains clashing tiles." }
    }

    private fun hasAnyClash(state: GameState): Boolean {
        val blooming = state.flowers.filter { !state.isGate(it.position) }
        for (i in blooming.indices) {
            for (j in i + 1 until blooming.size) {
                val a = blooming[i]
                val b = blooming[j]
                if (!flowersClash(a, b)) continue
                if (a.position.row != b.position.row && a.position.col != b.position.col) continue
                if (hasBlockingPoint(state, a.position, b.position)) continue
                return true
            }
        }
        return false
    }

    private fun flowersCanHarmonize(a: FlowerTile, b: FlowerTile): Boolean {
        if (a.type == TileType.WHITE_LOTUS && b.type.isBasic) return true
        if (b.type == TileType.WHITE_LOTUS && a.type.isBasic) return true
        if (a.owner != b.owner) return false
        if (!a.type.isBasic || !b.type.isBasic) return false
        val circle = listOf(
            TileType.ROSE,
            TileType.CHRYSANTHEMUM,
            TileType.RHODODENDRON,
            TileType.JASMINE,
            TileType.LILY,
            TileType.WHITE_JADE,
        )
        val ai = circle.indexOf(a.type)
        val bi = circle.indexOf(b.type)
        if (ai == -1 || bi == -1) return false
        val diff = abs(ai - bi)
        return diff == 1 || diff == 5
    }

    private fun flowersClash(a: FlowerTile, b: FlowerTile): Boolean {
        if (!a.type.isBasic || !b.type.isBasic) return false
        val oppositeColor = a.type.basicColor != b.type.basicColor
        return oppositeColor && a.type.sameNumberAs(b.type)
    }

    private fun harmonyOwner(a: FlowerTile, b: FlowerTile): Player {
        return when {
            a.type == TileType.WHITE_LOTUS && b.type.isBasic -> b.owner
            b.type == TileType.WHITE_LOTUS && a.type.isBasic -> a.owner
            else -> a.owner
        }
    }

    private fun canCapture(state: GameState, mover: FlowerTile, target: FlowerTile): Boolean {
        if (mover.owner == target.owner) return false
        if (mover.type == TileType.ORCHID && state.hasBloomingWhiteLotus(mover.owner)) return true
        if (target.type == TileType.ORCHID && state.hasBloomingWhiteLotus(target.owner)) return true
        return flowersClash(mover, target)
    }

    private fun canEndMoveOn(state: GameState, tile: FlowerTile, target: Position): Boolean {
        if (state.isGate(target) && target != tile.position) return false
        if (!state.isOnBoard(target)) return false
        if (tile.type.isBasic) {
            val garden = state.gardenColor(target)
            if (garden != GardenColor.NEUTRAL && garden != tile.type.basicColor) return false
        }
        return true
    }

    private fun hasBlockingPoint(state: GameState, a: Position, b: Position): Boolean {
        if (a.row == b.row) {
            val row = a.row
            val range = if (a.col < b.col) (a.col + 1) until b.col else (b.col + 1) until a.col
            return range.any { col ->
                val p = Position(row, col)
                state.isGate(p) || state.isOccupied(p)
            }
        }
        if (a.col == b.col) {
            val col = a.col
            val range = if (a.row < b.row) (a.row + 1) until b.row else (b.row + 1) until a.row
            return range.any { row ->
                val p = Position(row, col)
                state.isGate(p) || state.isOccupied(p)
            }
        }
        return true
    }

    private fun isHarmonyCancelledByRock(state: GameState, a: Position, b: Position): Boolean {
        val rocks = state.accents.filter { it.type == AccentType.ROCK }
        if (a.row == b.row) return rocks.any { it.position.row == a.row && between(it.position.col, a.col, b.col) }
        if (a.col == b.col) return rocks.any { it.position.col == a.col && between(it.position.row, a.row, b.row) }
        return false
    }

    private fun isHarmonyCancelledByKnotweed(state: GameState, a: Position, b: Position): Boolean {
        val knotweeds = state.accents.filter { it.type == AccentType.KNOTWEED }.map { it.position }
        return knotweeds.any { knot ->
            a in knot.surrounding8() || b in knot.surrounding8()
        }
    }

    private fun isTileTrappedByOpponentOrchid(state: GameState, tile: FlowerTile): Boolean {
        val opponents = state.flowersFor(tile.owner.other())
            .filter { it.type == TileType.ORCHID }
            .map { it.position }
        return opponents.any { orchidPos ->
            tile.position in orchidPos.surrounding8()
        }
    }

    private fun between(x: Int, a: Int, b: Int): Boolean =
        (x > minOf(a, b)) && (x < maxOf(a, b))

    private fun normalizedEdge(a: Position, b: Position): Pair<Position, Position> {
        return if (a.row < b.row || (a.row == b.row && a.col <= b.col)) a to b else b to a
    }

    private fun dfsRing(
        state: GameState,
        adjacency: Map<Position, Set<Position>>,
        prev: Position,
        current: Position,
        start: Position,
        usedEdges: MutableSet<Pair<Position, Position>>,
        center: Position,
    ): Boolean {
        val neighbors = adjacency[current].orEmpty()
        for (next in neighbors) {
            if (next == prev) continue
            val edge = normalizedEdge(current, next)
            if (edge in usedEdges) continue
            if (segmentTouchesCenter(current, next, center)) continue

            if (next == start) {
                val cycleEdges = usedEdges + edge
                if (cycleEnclosesCenter(cycleEdges, center)) return true
                continue
            }
            val nextUsed = usedEdges.toMutableSet()
            nextUsed += edge
            if (dfsRing(state, adjacency, current, next, start, nextUsed, center)) return true
        }
        return false
    }

    private fun segmentTouchesCenter(a: Position, b: Position, center: Position): Boolean {
        if (a == center || b == center) return true
        if (a.row == b.row && a.row == center.row && between(center.col, a.col, b.col)) return true
        if (a.col == b.col && a.col == center.col && between(center.row, a.row, b.row)) return true
        return false
    }

    private fun segmentPassesThroughCenter(state: GameState, a: Position, b: Position): Boolean {
        val center = Position(0, 0)
        return segmentTouchesCenter(a, b, center)
    }

    private fun cycleEnclosesCenter(
        cycleEdges: Set<Pair<Position, Position>>,
        center: Position,
    ): Boolean {
        val cycleNodes = cycleEdges.flatMap { listOf(it.first, it.second) }.toSet()
        if (cycleNodes.isEmpty()) return false
        val minRow = cycleNodes.minOf { it.row }
        val maxRow = cycleNodes.maxOf { it.row }
        val minCol = cycleNodes.minOf { it.col }
        val maxCol = cycleNodes.maxOf { it.col }
        return center.row in (minRow + 1) until maxRow &&
            center.col in minCol..maxCol &&
            cycleNodes.none { it == center }
    }
}
