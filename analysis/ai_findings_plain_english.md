# Skud Pai Sho AI Study: Plain-English Findings

This is a straightforward summary of what we learned from the replay assessment, without technical jargon.

## What data we reviewed

- We scanned **7,587 replay links** from your CSV.
- We could decode **7,570** of them.
- From those, we gathered and parsed **5,114 full games**.
- That gave us **142,893 total moves** to study.

In short: this is a large enough sample to spot strong, repeatable play patterns.

## High-level picture of how games are played

- Typical game length in this dataset is about **28 moves**.
- Some games end very quickly, while long games can run much deeper.
- Most turns are movement turns:
  - **~78% slide/move actions**
  - **~22% plant actions**

This means strong movement logic is more important than planting logic for most mid/late-game decisions.

## Opening behavior we observed

Players show very consistent opening habits:

- Most common early flower choices are:
  - **W5**
  - **R5**
  - **R3**
- Openings overwhelmingly use the four main gate coordinates:
  - **(0,-8), (0,8), (8,0), (-8,0)**

### What this means

Players are not opening randomly. There are clear preferred starts, and the AI should respect those patterns unless there is a strong tactical reason not to.

## Midgame movement behavior we observed

The most common movement vectors are short-to-medium directional shifts, especially:

- (-1,2)
- (1,-2)
- (-2,1)
- (0,3)
- (1,2)

### What this means

There are practical movement rhythms that appear repeatedly in real games. Biasing the AI toward these common movement shapes helps it choose more human-like and generally stronger candidate moves.

## Bonus-action behavior we observed

Most arranged moves **do not** include a bonus action, but when bonuses are used:

- Basic bonus plants are common.
- Special bonus plants happen regularly (but less than basics).
- Accent bonuses (Wheel/Rock/Knotweed) and Boat actions are used less often than basic no-bonus play.

### What this means

The AI should treat bonus actions as meaningful but not automatic. Over-forcing bonus choices can actually make play look less realistic than human match data.

## What changed in the AI because of this study

Based on this replay review, the AI now has data-driven preferences for:

- opening tile types
- opening gate positions
- common move vectors
- relative frequency of bonus categories

These preferences are used as **soft guidance**, not hard rules.  
The rules engine still enforces legality exactly.

## Why this should improve gameplay

- Better opening choices that mirror real play.
- More natural move selection in typical positions.
- Reduced chance of AI “hanging” in huge move trees due to better candidate prioritization.
- Stronger baseline behavior without removing future room for deeper search improvements.

## Important limits to keep in mind

- This study learns from observed human behavior, not from perfect-play labels.
- Some games in the source list were unavailable or malformed, so they were excluded.
- Priors improve consistency and practicality, but they are not a full strategic solver.

## Recommended next steps

1. Add outcome-aware learning (which choices correlate with wins).
2. Add position-context features (board control, harmony threats, reserve pressure).
3. Use this policy prior as move ordering for deeper lookahead search.
4. Re-train periodically as more replay data is collected.

