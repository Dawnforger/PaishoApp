#!/usr/bin/env python3
"""
Train lightweight policy priors from Skud Pai Sho replay links.

Pipeline:
1) Read CSV of replay URLs.
2) Decode compact link payloads (LZString).
3) Resolve notation directly (game=...) or via backend by game ID (ig=...).
4) Parse notation into move-level features.
5) Emit:
   - analysis/ai_priors.json
   - analysis/ai_training_report.md
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple
from urllib.parse import parse_qs, urlparse
from urllib.request import Request, urlopen

from lzstring import LZString


USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
NOTATION_URL = "https://skudpaisho.com/backend/getGameNotation.php?q={game_id}"

# Parsed replay notation forms.
TURN_RE = re.compile(r"^(\d+)([HG])\.(.+)$")
MOVE_SLIDE_RE = re.compile(r"^\((-?\d+),(-?\d+)\)-\((-?\d+),(-?\d+)\)$")
MOVE_PLANT_RE = re.compile(r"^(R3|R4|R5|W3|W4|W5|L|O)\((-?\d+),(-?\d+)\)$")
BONUS_BOAT_MOVE_RE = re.compile(r"^B\((-?\d+),(-?\d+)\)-\((-?\d+),(-?\d+)\)$")
BONUS_SINGLE_RE = re.compile(r"^(R3|R4|R5|W3|W4|W5|L|O|R|W|K|B)\((-?\d+),(-?\d+)\)$")


@dataclass(frozen=True)
class MoveSample:
    player: str  # "H" or "G"
    turn: int
    action: str  # "plant" | "slide"
    plant_code: Optional[str]
    target: Optional[Tuple[int, int]]
    delta: Optional[Tuple[int, int]]
    bonus_kind: str
    bonus_code: Optional[str]


def decode_payload(url: str, lz: LZString) -> Optional[str]:
    query = urlparse(url).query
    if not query:
        return None
    try:
        return lz.decompressFromEncodedURIComponent(query)
    except Exception:
        return None


def fetch_notation_for_game_id(game_id: str, timeout_s: float = 10.0) -> Optional[str]:
    url = NOTATION_URL.format(game_id=game_id)
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "text/plain,text/html,*/*",
    }
    # Keep retries light to avoid overloading remote service.
    for attempt in range(3):
        try:
            req = Request(url=url, headers=headers, method="GET")
            with urlopen(req, timeout=timeout_s) as response:
                body = response.read().decode("utf-8", errors="replace").strip()
            if body and ";" in body and "." in body:
                return body
            return None
        except Exception:
            if attempt == 2:
                return None
            time.sleep(0.5 * (2**attempt))
    return None


def split_move_and_bonus(raw: str) -> Tuple[str, Optional[str]]:
    # Bonus is always expressed as +<bonus> suffix in notation.
    plus_index = raw.find("+")
    if plus_index == -1:
        return raw, None
    return raw[:plus_index], raw[plus_index + 1 :]


def parse_bonus_kind(bonus: Optional[str]) -> Tuple[str, Optional[str]]:
    if not bonus:
        return "none", None
    boat_move_match = BONUS_BOAT_MOVE_RE.match(bonus)
    if boat_move_match:
        return "boat_move", "B"
    single = BONUS_SINGLE_RE.match(bonus)
    if not single:
        return "unknown", None
    code = single.group(1)
    if code == "B":
        return "boat_remove", code
    if code in {"R", "W", "K"}:
        return f"accent_{code}", code
    if code in {"L", "O"}:
        return "plant_special", code
    if re.match(r"^[RW]\d$", code):
        return "plant_basic", code
    return "unknown", code


def parse_move_sample(turn: int, player: str, body: str) -> Optional[MoveSample]:
    move_part, bonus_part = split_move_and_bonus(body.strip())
    bonus_kind, bonus_code = parse_bonus_kind(bonus_part)

    slide_match = MOVE_SLIDE_RE.match(move_part)
    if slide_match:
        sr, sc, tr, tc = map(int, slide_match.groups())
        return MoveSample(
            player=player,
            turn=turn,
            action="slide",
            plant_code=None,
            target=(tr, tc),
            delta=(tr - sr, tc - sc),
            bonus_kind=bonus_kind,
            bonus_code=bonus_code,
        )

    plant_match = MOVE_PLANT_RE.match(move_part)
    if plant_match:
        code, tr, tc = plant_match.groups()
        return MoveSample(
            player=player,
            turn=turn,
            action="plant",
            plant_code=code,
            target=(int(tr), int(tc)),
            delta=None,
            bonus_kind=bonus_kind,
            bonus_code=bonus_code,
        )
    return None


def parse_notation_to_samples(notation: str) -> List[MoveSample]:
    samples: List[MoveSample] = []
    for token in notation.split(";"):
        token = token.strip()
        if not token:
            continue
        turn_match = TURN_RE.match(token)
        if not turn_match:
            continue
        turn, player, body = turn_match.groups()
        turn_num = int(turn)
        if turn_num == 0:
            # Accent loadout declaration, not a board move.
            continue
        sample = parse_move_sample(turn_num, player, body)
        if sample:
            samples.append(sample)
    return samples


def normalize_counter(counter: Counter) -> Dict[str, float]:
    total = sum(counter.values())
    if total == 0:
        return {}
    return {k: v / total for k, v in counter.items()}


def log_weight(counter: Counter, keys: Iterable[str]) -> Dict[str, float]:
    keys = list(keys)
    total = sum(counter.values())
    denom = total + len(keys)
    if denom <= 0:
        return {k: 0.0 for k in keys}
    return {k: math.log((counter.get(k, 0) + 1) / denom) for k in keys}


def gate_key(pos: Tuple[int, int]) -> str:
    return f"{pos[0]},{pos[1]}"


def delta_key(delta: Tuple[int, int]) -> str:
    return f"{delta[0]},{delta[1]}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train Skud Pai Sho move priors from replay links.")
    parser.add_argument(
        "--input-csv",
        default="/home/ubuntu/.cursor/projects/workspace/uploads/skud_links_unique.csv",
        help="Path to CSV containing replay URLs.",
    )
    parser.add_argument(
        "--out-priors",
        default="analysis/ai_priors.json",
        help="Output path for learned priors JSON.",
    )
    parser.add_argument(
        "--out-report",
        default="analysis/ai_training_report.md",
        help="Output path for markdown findings report.",
    )
    parser.add_argument(
        "--max-games",
        type=int,
        default=0,
        help="Optional cap on number of games to process (0 = all available).",
    )
    parser.add_argument(
        "--fetch-workers",
        type=int,
        default=16,
        help="Concurrent worker count for remote notation fetches.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    csv_path = Path(args.input_csv)
    out_priors = Path(args.out_priors)
    out_report = Path(args.out_report)
    out_priors.parent.mkdir(parents=True, exist_ok=True)
    out_report.parent.mkdir(parents=True, exist_ok=True)

    lz = LZString()

    rows_total = 0
    decode_ok = 0
    decode_failed = 0
    inline_notations: List[str] = []
    game_ids: List[str] = []

    with csv_path.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            rows_total += 1
            payload = decode_payload(row.get("url", ""), lz)
            if not payload:
                decode_failed += 1
                continue
            decode_ok += 1
            params = parse_qs(payload, keep_blank_values=True)
            if "game" in params and params["game"] and params["game"][0].strip():
                inline_notations.append(params["game"][0].strip())
            if "ig" in params and params["ig"] and params["ig"][0].strip():
                game_ids.append(params["ig"][0].strip())

    unique_game_ids = sorted(set(game_ids))
    fetched_notations: Dict[str, str] = {}
    fetch_failures = 0

    if unique_game_ids:
        with ThreadPoolExecutor(max_workers=max(1, args.fetch_workers)) as pool:
            futures = {
                pool.submit(fetch_notation_for_game_id, game_id): game_id
                for game_id in unique_game_ids
            }
            for future in as_completed(futures):
                game_id = futures[future]
                notation = future.result()
                if notation:
                    fetched_notations[game_id] = notation
                else:
                    fetch_failures += 1

    # Deduplicate notation strings; some links reference same game.
    all_notations = list(dict.fromkeys(inline_notations + list(fetched_notations.values())))
    if args.max_games > 0:
        all_notations = all_notations[: args.max_games]

    game_move_counts: List[int] = []
    samples: List[MoveSample] = []
    parse_fail_games = 0
    for notation in all_notations:
        game_samples = parse_notation_to_samples(notation)
        if not game_samples:
            parse_fail_games += 1
            continue
        samples.extend(game_samples)
        game_move_counts.append(len(game_samples))

    gate_positions = {
        (-8, 0),
        (8, 0),
        (0, -8),
        (0, 8),
    }

    # Aggregate features.
    action_counter = Counter(sample.action for sample in samples)
    plant_code_counter = Counter(sample.plant_code for sample in samples if sample.action == "plant" and sample.plant_code)
    gate_counter = Counter(
        gate_key(sample.target)
        for sample in samples
        if sample.action == "plant" and sample.target and sample.target in gate_positions
    )
    slide_delta_counter = Counter(delta_key(sample.delta) for sample in samples if sample.action == "slide" and sample.delta)
    bonus_kind_counter = Counter(sample.bonus_kind for sample in samples)

    # Opening-specific trends (first four turns only).
    opening_samples = [sample for sample in samples if sample.turn <= 4]
    opening_plant_counter = Counter(
        sample.plant_code for sample in opening_samples if sample.action == "plant" and sample.plant_code
    )
    opening_gate_counter = Counter(
        gate_key(sample.target)
        for sample in opening_samples
        if sample.action == "plant" and sample.target and sample.target in gate_positions
    )

    allowed_plant_codes = ["R3", "R4", "R5", "W3", "W4", "W5", "L", "O"]
    allowed_bonus_kinds = [
        "none",
        "accent_R",
        "accent_W",
        "accent_K",
        "boat_move",
        "boat_remove",
        "plant_basic",
        "plant_special",
        "unknown",
    ]
    gate_keys = sorted(gate_counter.keys())
    delta_keys = [k for k, _ in slide_delta_counter.most_common(40)]

    priors = {
        "metadata": {
            "rows_total": rows_total,
            "decode_ok": decode_ok,
            "decode_failed": decode_failed,
            "inline_notation_count": len(inline_notations),
            "unique_game_ids": len(unique_game_ids),
            "fetched_notation_count": len(fetched_notations),
            "fetch_failures": fetch_failures,
            "unique_games_used": len(game_move_counts),
            "parse_fail_games": parse_fail_games,
            "total_move_samples": len(samples),
        },
        "frequencies": {
            "actions": dict(action_counter),
            "plant_codes": dict(plant_code_counter),
            "plant_gates": dict(gate_counter),
            "slide_deltas_top40": {k: slide_delta_counter[k] for k in delta_keys},
            "bonus_kinds": dict(bonus_kind_counter),
            "opening_plant_codes_turn_le_4": dict(opening_plant_counter),
            "opening_gates_turn_le_4": dict(opening_gate_counter),
        },
        "probabilities": {
            "actions": normalize_counter(action_counter),
            "plant_codes": normalize_counter(plant_code_counter),
            "plant_gates": normalize_counter(gate_counter),
            "bonus_kinds": normalize_counter(bonus_kind_counter),
            "opening_plant_codes_turn_le_4": normalize_counter(opening_plant_counter),
            "opening_gates_turn_le_4": normalize_counter(opening_gate_counter),
        },
        "log_priors_for_ai": {
            "plant_codes": log_weight(plant_code_counter, allowed_plant_codes),
            "plant_gates": log_weight(gate_counter, gate_keys),
            "opening_plant_codes_turn_le_4": log_weight(opening_plant_counter, allowed_plant_codes),
            "opening_gates_turn_le_4": log_weight(opening_gate_counter, gate_keys),
            "slide_deltas_top40": log_weight(slide_delta_counter, delta_keys),
            "bonus_kinds": log_weight(bonus_kind_counter, allowed_bonus_kinds),
        },
    }

    out_priors.write_text(json.dumps(priors, indent=2, sort_keys=True), encoding="utf-8")

    avg_moves = (sum(game_move_counts) / len(game_move_counts)) if game_move_counts else 0.0
    min_moves = min(game_move_counts) if game_move_counts else 0
    max_moves = max(game_move_counts) if game_move_counts else 0

    top_opening_plants = opening_plant_counter.most_common(6)
    top_opening_gates = opening_gate_counter.most_common(6)
    top_slide_deltas = slide_delta_counter.most_common(10)
    top_bonus = bonus_kind_counter.most_common(10)

    report_lines = [
        "# Skud Pai Sho Replay Study Report",
        "",
        "## Dataset coverage",
        f"- CSV rows scanned: **{rows_total}**",
        f"- Successfully decoded links: **{decode_ok}**",
        f"- Failed/empty decodes: **{decode_failed}**",
        f"- Inline notation links (`game=`): **{len(inline_notations)}**",
        f"- Unique backend game IDs (`ig=`): **{len(unique_game_ids)}**",
        f"- Backend notations fetched: **{len(fetched_notations)}**",
        f"- Backend fetch failures: **{fetch_failures}**",
        f"- Unique games parsed into move samples: **{len(game_move_counts)}**",
        f"- Total move samples: **{len(samples)}**",
        "",
        "## Game length profile",
        f"- Average moves per game: **{avg_moves:.2f}**",
        f"- Min moves: **{min_moves}**",
        f"- Max moves: **{max_moves}**",
        "",
        "## Learned tendencies",
        "",
        "### Opening tile preferences (turn <= 4)",
    ]
    for code, count in top_opening_plants:
        report_lines.append(f"- {code}: {count}")
    report_lines += [
        "",
        "### Opening gate preferences (turn <= 4)",
    ]
    for gate, count in top_opening_gates:
        report_lines.append(f"- ({gate}): {count}")
    report_lines += [
        "",
        "### Common slide vectors (dr,dc)",
    ]
    for delta, count in top_slide_deltas:
        report_lines.append(f"- ({delta}): {count}")
    report_lines += [
        "",
        "### Bonus action usage",
    ]
    for bonus, count in top_bonus:
        report_lines.append(f"- {bonus}: {count}")
    report_lines += [
        "",
        "## Notes",
        "- The extracted priors are intentionally lightweight and policy-oriented (move tendency bias).",
        "- This model is integrated as additive scoring terms in `SimpleAi`, preserving full rules legality from the Kotlin rules engine.",
        f"- Machine-readable priors were written to `{out_priors.as_posix()}`.",
        "",
    ]
    out_report.write_text("\n".join(report_lines), encoding="utf-8")

    print(f"Wrote priors JSON: {out_priors}")
    print(f"Wrote markdown report: {out_report}")
    print(f"Games used: {len(game_move_counts)}, move samples: {len(samples)}")


if __name__ == "__main__":
    main()
