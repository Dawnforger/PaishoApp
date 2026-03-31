# Skud Pai Sho Replay Study Report

## Dataset coverage
- CSV rows scanned: **7587**
- Successfully decoded links: **7570**
- Failed/empty decodes: **17**
- Inline notation links (`game=`): **201**
- Unique backend game IDs (`ig=`): **5650**
- Backend notations fetched: **4930**
- Backend fetch failures: **720**
- Unique games parsed into move samples: **5114**
- Total move samples: **142893**

## Game length profile
- Average moves per game: **27.94**
- Min moves: **1**
- Max moves: **151**

## Learned tendencies

### Opening tile preferences (turn <= 4)
- W5: 5770
- R5: 5089
- R3: 4849
- R4: 2928
- W3: 2706
- W4: 1650

### Opening gate preferences (turn <= 4)
- (0,-8): 6702
- (0,8): 6177
- (8,0): 5182
- (-8,0): 4914

### Common slide vectors (dr,dc)
- (-1,2): 3850
- (1,-2): 3435
- (-2,1): 3295
- (0,3): 3241
- (1,2): 3157
- (-1,1): 3118
- (-1,-2): 3068
- (2,1): 3030
- (2,-1): 2869
- (1,1): 2861

### Bonus action usage
- none: 93548
- plant_basic: 26032
- plant_special: 8661
- accent_W: 3528
- accent_R: 3425
- accent_K: 3403
- boat_move: 2128
- boat_remove: 2032
- unknown: 136

## Notes
- The extracted priors are intentionally lightweight and policy-oriented (move tendency bias).
- This model is integrated as additive scoring terms in `SimpleAi`, preserving full rules legality from the Kotlin rules engine.
- Machine-readable priors were written to `analysis/ai_priors.json`.
