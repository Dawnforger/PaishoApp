package com.paisho.core.game

object Rules {
    /**
     * Simplified MVP rules inspired by Skud Pai Sho:
     * - During planting, players place on any empty edge intersection.
     * - After both players have completed opening plants, game enters movement phase.
     * - Movement is orthogonal up to tile range; no jumping over tiles.
     * - Landing on an opponent tile captures it; own-tile landing is illegal.
     * - Win condition (MVP): opponent has no active tiles.
     */
    fun legalMoves(state: GameState): List<Move> {
        if (state.phase == GamePhase.FINISHED || state.winner != null) return emptyList()
        return when (state.phase) {
            GamePhase.PLANTING -> legalPlants(state)
            GamePhase.MOVEMENT -> legalSlides(state)
            GamePhase.FINISHED -> emptyList()
        }
    }

    fun legalMovesFrom(state: GameState, from: Position): List<Move.Slide> {
        val tile = state.tileAt(from) ?: return emptyList()
        if (tile.owner != state.currentPlayer || state.phase != GamePhase.MOVEMENT) return emptyList()
        return legalSlidesForTile(state, tile)
    }

    private fun legalPlants(state: GameState): List<Move.Plant> {
        val ownerCount = state.tilesFor(state.currentPlayer).size
        if (ownerCount >= state.rules.maxActiveTilesPerPlayer) return emptyList()

        return buildList {
            for (row in 0 until state.rules.boardSize) {
                for (col in 0 until state.rules.boardSize) {
                    val position = Position(row, col)
                    if (!position.isEdge(state.rules.boardSize)) continue
                    if (state.tileAt(position) != null) continue
                    TileType.entries.forEach { type ->
                        add(Move.Plant(type, position))
                    }
                }
            }
        }
    }

    private fun legalSlides(state: GameState): List<Move.Slide> {
        return state.tilesFor(state.currentPlayer)
            .flatMap { tile -> legalSlidesForTile(state, tile) }
    }

    private fun legalSlidesForTile(state: GameState, tile: Tile): List<Move.Slide> {
        val directions = listOf(
            Position(1, 0),
            Position(-1, 0),
            Position(0, 1),
            Position(0, -1),
        )
        val moves = mutableListOf<Move.Slide>()
        directions.forEach { direction ->
            for (step in 1..tile.type.maxMoveDistance) {
                val target = Position(
                    row = tile.position.row + direction.row * step,
                    col = tile.position.col + direction.col * step,
                )
                if (!state.isOnBoard(target)) break

                val occupant = state.tileAt(target)
                when {
                    occupant == null -> moves += Move.Slide(tile.id, target)
                    occupant.owner == tile.owner -> break
                    else -> {
                        moves += Move.Slide(tile.id, target)
                        break
                    }
                }
            }
        }
        return moves
    }

    fun applyMove(state: GameState, move: Move): GameState {
        require(move in legalMoves(state)) { "Illegal move: $move" }

        val movedState = when (move) {
            is Move.Plant -> applyPlant(state, move)
            is Move.Slide -> applySlide(state, move)
        }
        val openingComplete = movedState.phase == GamePhase.PLANTING &&
            movedState.plantsUsedHuman >= movedState.rules.openingPlantTurnsPerPlayer &&
            movedState.plantsUsedAi >= movedState.rules.openingPlantTurnsPerPlayer
        val effectivePhase = if (openingComplete) GamePhase.MOVEMENT else movedState.phase
        val winner = if (effectivePhase == GamePhase.MOVEMENT) determineWinner(movedState.tiles) else null
        val nextPhase = if (winner != null) GamePhase.FINISHED else effectivePhase

        return movedState.copy(
            winner = winner,
            phase = nextPhase,
            currentPlayer = if (winner == null) movedState.currentPlayer.other() else movedState.currentPlayer,
            turnNumber = movedState.turnNumber + 1,
        )
    }

    private fun applyPlant(state: GameState, move: Move.Plant): GameState {
        val tile = Tile(
            id = state.nextTileId,
            owner = state.currentPlayer,
            type = move.type,
            position = move.target,
        )
        return state.copy(
            tiles = state.tiles + tile,
            nextTileId = state.nextTileId + 1,
            plantsUsedHuman = state.plantsUsedHuman + if (state.currentPlayer == Player.HUMAN) 1 else 0,
            plantsUsedAi = state.plantsUsedAi + if (state.currentPlayer == Player.AI) 1 else 0,
        )
    }

    private fun applySlide(state: GameState, move: Move.Slide): GameState {
        val movingTile = state.tiles.firstOrNull { it.id == move.tileId && it.owner == state.currentPlayer }
            ?: error("No tile found for move $move")

        val survivors = state.tiles.filterNot { tile ->
            tile.id == movingTile.id || tile.position == move.target
        }
        val moved = movingTile.copy(position = move.target)
        return state.copy(tiles = survivors + moved)
    }

    fun determineWinner(tiles: List<Tile>): Player? {
        val humanCount = tiles.count { it.owner == Player.HUMAN }
        val aiCount = tiles.count { it.owner == Player.AI }
        return when {
            humanCount == 0 && aiCount == 0 -> null
            humanCount == 0 -> Player.AI
            aiCount == 0 -> Player.HUMAN
            else -> null
        }
    }
}
