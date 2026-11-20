#!/usr/bin/env python3
"""Builds app/src/main/assets/pin_in.txt: every Indian PIN code with its state and district,
so the app can fill both in on the phone when a worker types their PIN.

Source: India Post's "All India Pincode Directory" (data.gov.in, Government Open Data
Licence - India; attribution: India Post, Department of Posts). Get the CSV from
https://data.gov.in/resource/all-india-pincode-directory-till-last-month (or a mirror of it)
and run, from android/:

    python tools/make_pin_directory.py <pincode.csv> tools/districts_in.txt app/src/main/assets/pin_in.txt

India Post still uses some district names from before recent reorganisations (Andhra Pradesh
went from 13 to 26 districts in 2022). For the app's ten work states the output uses today's
district names (tools/districts_in.txt, checked against the state governments' lists). Where an old postal district is now split, a PIN lists all
the current districts it may be in, best guess first, and the worker confirms. Other states use
India Post's own district names.

Output format:
    S|<state>|<district>;<district>;...            one line per state, in order (index 0, 1, ...)
    <pin>|<state index>|[*]<district index>[,<district index>...][|<lat>,<lon>]
        (* = the first is a confident guess; lat,lon = the middle of its post offices, for "use my
        location"; left out where India Post has no usable position)
"""
import collections
import csv
import math
import re
import statistics
import sys
from datetime import date

# Spellings India Post uses -> the app's names (districts_in.txt).
ALIASES = {
    "Odisha": {"ANUGUL": "Angul", "JAGATSINGHAPUR": "Jagatsinghpur", "JAJAPUR": "Jajpur"},
    "Telangana": {"JANGOAN": "Jangaon", "JAYASHANKAR BHUPALAPALLY": "Jayashankar Bhupalpally", "JAGITIAL": "Jagtial"},
    "Karnataka": {"BAGALKOTE": "Bagalkot", "CHAMARAJANAGARA": "Chamarajanagar", "CHIKKABALLAPURA": "Chikkaballapur", "DAVANGERE": "Davanagere"},
    "Tamil Nadu": {"KANCHIPURAM": "Kancheepuram", "THE NILGIRIS": "Nilgiris", "THIRUVALLUR": "Tiruvallur", "THIRUVARUR": "Tiruvarur"},
    "Gujarat": {"AHMADABAD": "Ahmedabad", "ARVALLI": "Aravalli", "CHHOTAUDEPUR": "Chhota Udaipur", "DOHAD": "Dahod",
                "KACHCHH": "Kutch", "MAHESANA": "Mehsana", "PANCH MAHALS": "Panchmahal"},
    "West Bengal": {"24 PARAGANAS NORTH": "North 24 Parganas", "24 PARAGANAS SOUTH": "South 24 Parganas", "DINAJPUR DAKSHIN": "Dakshin Dinajpur",
                    "DINAJPUR UTTAR": "Uttar Dinajpur", "MALDAH": "Malda", "MEDINIPUR EAST": "Purba Medinipur", "MEDINIPUR WEST": "Paschim Medinipur"},
}

# Old postal districts that are now split: every current district a PIN in them may be in.
SPLITS = {
    "Andhra Pradesh": {  # 2022 reorganisation, 13 -> 26 districts
        "SRIKAKULAM": ["Srikakulam", "Parvathipuram Manyam"],
        "VIZIANAGARAM": ["Vizianagaram", "Parvathipuram Manyam"],
        "VISAKHAPATANAM": ["Visakhapatnam", "Anakapalli", "Alluri Sitharama Raju"],
        "EAST GODAVARI": ["East Godavari", "Kakinada", "Dr. B.R. Ambedkar Konaseema", "Alluri Sitharama Raju"],
        "WEST GODAVARI": ["West Godavari", "Eluru"],
        "KRISHNA": ["Krishna", "NTR", "Eluru"],
        "GUNTUR": ["Guntur", "Palnadu", "Bapatla"],
        "PRAKASAM": ["Prakasam", "Bapatla", "Sri Potti Sriramulu Nellore (Nellore)"],
        "SPSR NELLORE": ["Sri Potti Sriramulu Nellore (Nellore)", "Tirupati"],
        "KURNOOL": ["Kurnool", "Nandyal"],
        "ANANTAPUR": ["Anantapur (Ananthapuramu)", "Sri Sathya Sai"],
        "Y.S.R.": ["YSR Kadapa (Kadapa)", "Annamayya"],
        "CHITTOOR": ["Chittoor", "Tirupati", "Annamayya"],
    },
    "Telangana": {"WARANGAL URBAN": ["Hanumakonda", "Warangal"], "WARANGAL RURAL": ["Warangal", "Hanumakonda"]},
    "Karnataka": {"BALLARI": ["Ballari (Bellary)", "Vijayanagara"]},
    "Gujarat": {"BANASKANTHA": ["Banaskantha", "Vav-Tharad"]},
    "Maharashtra": {"MUMBAI": ["Mumbai City", "Mumbai Suburban"]},
}


# Headquarters towns of districts made by those splits: a PIN whose post offices are named after
# one is in that district. Used only to put the likely district first.
HQ = {
    "Parvathipuram Manyam": ["parvathipuram", "parvatipuram"], "Anakapalli": ["anakapalli", "anakapalle"],
    "Alluri Sitharama Raju": ["paderu"], "Kakinada": ["kakinada"], "Dr. B.R. Ambedkar Konaseema": ["amalapuram"],
    "Eluru": ["eluru"], "NTR": ["vijayawada"], "Palnadu": ["narasaraopet", "narasaraopeta"], "Bapatla": ["bapatla"],
    "Tirupati": ["tirupati", "tirupathi"], "Nandyal": ["nandyal"], "Sri Sathya Sai": ["puttaparthi", "puttaparthy"],
    "Annamayya": ["rayachoti"], "Hanumakonda": ["hanamkonda", "hanumakonda"], "Vijayanagara": ["hospet", "hosapete"],
    "Vav-Tharad": ["tharad"], "Mumbai Suburban": ["bandra", "andheri", "borivali", "kurla", "ghatkopar", "mulund", "goregaon", "malad", "kandivali", "santacruz", "vile parle", "chembur"],
}


def state_name(raw: str) -> str:
    """Same names as StateInfo.normalize in the app."""
    s = re.sub(r"\s+", " ", raw.strip())
    if s.upper().startswith("THE "):
        s = s[4:]
    k = s.lower()
    if "delhi" in k:
        return "Delhi"
    out = " ".join(w[:1].upper() + w[1:].lower() for w in s.split(" "))
    return out.replace(" And ", " and ").replace(" Of ", " of ")


def title(raw: str) -> str:
    return " ".join(w[:1].upper() + w[1:].lower() for w in re.sub(r"\s+", " ", raw.strip()).split(" "))


def key(s: str) -> str:
    return "".join(c for c in s.lower().split("(")[0] if c.isalpha())


def main(csv_path: str, curated_path: str, out_path: str) -> None:
    curated = {}
    for line in open(curated_path, encoding="utf-8"):
        if "|" in line and not line.startswith("#"):
            st, ds = line.strip().split("|", 1)
            curated[st] = [d for d in ds.split(";") if d]

    def current(state: str, raw_district: str) -> list[str]:
        if state not in curated:
            return [title(raw_district)]
        up = raw_district.upper()
        if up in SPLITS.get(state, {}):
            return SPLITS[state][up]
        if up in ALIASES.get(state, {}):
            return [ALIASES[state][up]]
        k = key(raw_district)
        for d in curated[state]:
            names = [d] + re.findall(r"\(([^)]*)\)", d)
            if any(key(n) == k for n in names):
                return [d]
        raise SystemExit(f"{state}: no current district for postal district {raw_district!r}: add it to ALIASES or SPLITS")

    offices = collections.defaultdict(list)  # pin -> [(state, district, office name)]
    points = collections.defaultdict(list)   # pin -> [(lat, lon)] of its post offices
    with open(csv_path, encoding="utf-8", errors="replace") as f:
        for r in csv.DictReader(f):
            pin = (r.get("Pincode") or "").strip()
            # 9xxxxx is the Army Postal Service: not a place (the few such rows are data errors).
            if re.fullmatch(r"[1-8]\d{5}", pin) and r.get("StateName") and r.get("District"):
                offices[pin].append((state_name(r["StateName"]), r["District"].strip(), r.get("OfficeName", "")))
                try:
                    la, lo = float(r.get("Latitude") or ""), float(r.get("Longitude") or "")
                except ValueError:
                    continue
                if 6 <= la <= 37.6 and 68 <= lo <= 97.5:  # inside India's box
                    points[pin].append((la, lo))

    districts = collections.defaultdict(set)
    for st, ds in curated.items():
        districts[st].update(ds)
    pins = {}
    sure = {}
    multi = 0
    for pin, rows in offices.items():
        state = collections.Counter(s for s, _, _ in rows).most_common(1)[0][0]  # rare PINs straddle two states
        cands: list[str] = []
        for s, d, _ in rows:
            if s == state:
                for c in current(state, d):
                    if c not in cands:
                        cands.append(c)
        if len(cands) > 1:
            multi += 1
            # A town that gives its name to a district (Tirupati, Eluru…) is in that district: best guess first.
            names = re.sub(r"[^a-z]", "", " ".join(o.lower() for s, _, o in rows if s == state))
            towns = {c: [key(c)] + [re.sub(r"[^a-z]", "", t) for t in HQ.get(c, [])] for c in cands}
            named = [c for c in cands if any(len(t) >= 4 and t in names for t in towns[c])]
            if len(named) == 1:
                cands.remove(named[0])
                cands.insert(0, named[0])
                sure[pin] = True
        districts[state].update(cands)
        pins[pin] = (state, cands)

    # Where each PIN is: the middle (median) of its post offices. A PIN far from the other PINs
    # sharing its first three digits (its sorting area) has a wrong position: left out.
    where = {p: (statistics.median(a for a, _ in v), statistics.median(b for _, b in v)) for p, v in points.items() if p in pins}
    def km(a, b):
        x = math.radians(b[1] - a[1]) * math.cos(math.radians((a[0] + b[0]) / 2))
        return 6371 * math.hypot(x, math.radians(b[0] - a[0]))
    areas = collections.defaultdict(list)
    for p, c in where.items():
        areas[p[:3]].append(c)
    area = {k: (statistics.median(a for a, _ in v), statistics.median(b for _, b in v)) for k, v in areas.items()}
    dropped = [p for p, c in where.items() if km(c, area[p[:3]]) > 150]
    for p in dropped:
        del where[p]

    states = sorted(districts)
    order = {st: sorted(districts[st], key=str.lower) for st in states}
    with open(out_path, "w", encoding="utf-8") as out:
        out.write(f"# Indian PIN codes -> state and district. Source: India Post, All India Pincode Directory\n")
        out.write(f"# (data.gov.in, Government Open Data Licence - India). Built {date.today()} by tools/make_pin_directory.py.\n")
        out.write(f"# District names: today's names for the app's work states; India Post's names elsewhere.\n")
        for st in states:
            out.write(f"S|{st}|{';'.join(order[st])}\n")
        si = {st: i for i, st in enumerate(states)}
        for pin in sorted(pins):
            st, cands = pins[pin]
            # "*": the first district is a confident guess (its town is named); otherwise the worker chooses.
            pos = f"|{where[pin][0]:.3f},{where[pin][1]:.3f}" if pin in where else ""
            out.write(f"{pin}|{si[st]}|{'*' if sure.get(pin) else ''}{','.join(str(order[st].index(c)) for c in cands)}{pos}\n")
    print(f"{len(pins)} PIN codes, {len(states)} states/UTs, {sum(len(v) for v in order.values())} districts; "
          f"{multi} PINs may be in more than one district, {len(sure)} of them with a confident first guess; "
          f"{len(where)} with a position ({len(dropped)} wrong positions left out)")


if __name__ == "__main__":
    main(*sys.argv[1:4])
