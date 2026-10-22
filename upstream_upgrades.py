#!/usr/bin/env python3
"""Record the upstream models a snapshot refresh replaces, so installs of the old
model are offered the new one as an upgrade instead of silently keeping it.

Run it before overwriting data_sources/gcs_models.json:

    python3 upstream_upgrades.py data_sources/gcs_models.json new_models.json
    cp new_models.json data_sources/gcs_models.json

For every pair whose selected model changes, the old entry is stored in
data_sources/upstream_previous.json; generate_index.py then ships it as the
priority-1 variant (legacy flat install path) with the new model as priority 2.
Pairs that already have an entry keep their original one: that is the model
still sitting at the flat path on older installs.
"""

import json
import sys
from pathlib import Path

import catalog_upstream

PREVIOUS_PATH = Path(__file__).resolve().parent / "data_sources/upstream_previous.json"
METRICS = ("chrf", "comet22", "metricx24")


def model_path(entry: dict) -> str:
    return entry["files"]["model"]["path"]


def metrics(entry: dict) -> dict:
    return entry.get("metrics", {}).get("flores200-plus", {})


def main() -> None:
    old_path, new_path = sys.argv[1:3]
    old = json.loads(Path(old_path).read_text())["models"]
    new = json.loads(Path(new_path).read_text())["models"]
    previous = json.loads(PREVIOUS_PATH.read_text()) if PREVIOUS_PATH.exists() else {
        "_comment": "Upstream models replaced by a snapshot refresh; shipped as the priority-1 variant under the newer model.",
        "models": {},
    }

    for pair in sorted(set(old) & set(new)):
        before = catalog_upstream.select_best_entry(old[pair])
        after = catalog_upstream.select_best_entry(new[pair])
        if model_path(before) == model_path(after):
            continue
        mb, ma = metrics(before), metrics(after)
        shown = "  ".join(f"{m} {mb[m]:.3f}->{ma[m]:.3f}" for m in METRICS if m in mb and m in ma)
        kept = pair in previous["models"]
        print(f"{pair:7} {shown}{'  (keeps earlier previous entry)' if kept else ''}")
        if not kept:
            previous["models"][pair] = [before]

    PREVIOUS_PATH.write_text(json.dumps(previous, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
