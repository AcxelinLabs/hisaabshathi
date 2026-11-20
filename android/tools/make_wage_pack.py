#!/usr/bin/env python3
"""Builds app/src/main/assets/wage_pack_in.json from the open minimum-wage dataset.

    python tools/make_wage_pack.py ../data/minimum-wages/minimum_wages_in.csv app/src/main/assets/wage_pack_in.json

The CSV is the source of truth (one row per rate, with its notice and how it was checked);
see data/minimum-wages/README.md. Rows are kept in the CSV's order.
"""
import csv
import json
import sys
from datetime import date

REGIONS = ["Odisha", "Telangana", "Karnataka", "Andhra Pradesh", "Tamil Nadu", "Kerala",
           "Maharashtra", "Gujarat", "Delhi", "West Bengal", "Other"]


def main(src: str, out: str) -> None:
    rates = []
    with open(src, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            assert r["confidence"] in ("high", "medium", "low"), r
            assert r["source"] and r["effective_from"] and r["checked"], r
            rates.append({
                "region": r["region"], "zone": r["zone"], "sector": r["sector"], "skill": r["skill"],
                "dailyRupees": round(float(r["daily_rupees"]), 2),
                "effectiveFrom": r["effective_from"], "validTo": r["valid_to"],
                "confidence": r["confidence"], "source": r["source"], "url": r["url"],
            })
    missing = [s for s in REGIONS[:-1] if not any(x["region"] == s for x in rates)]
    assert not missing, f"no rates for {missing}"
    pack = {
        "version": 3,
        "updated": date.today().isoformat(),
        "note": "Built from data/minimum-wages/minimum_wages_in.csv. 'high': read from the government "
                "notice itself; 'medium': part read from the notice or several summaries agree; 'low': "
                "from summaries only, still to be checked against the notice.",
        "regions": REGIONS,
        "rates": rates,
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(pack, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{len(rates)} rates for {len({x['region'] for x in rates})} regions -> {out}")


if __name__ == "__main__":
    main(*sys.argv[1:3])
