# Minimum wages in India: open dataset

The minimum wages HisaabSaathi checks a worker's pay against, for ten states and the central
government, with the government notice behind every figure and how that figure was checked.

- **File:** `minimum_wages_in.csv`, one row per rate.
- **In the app:** `android/tools/make_wage_pack.py` turns it into
  `android/app/src/main/assets/wage_pack_in.json`.
- **Licence:** the figures are public government notices. This compilation is released under
  CC-BY-4.0: credit "HisaabSaathi, Acxelin Labs".

## Columns

| Column | Meaning |
|---|---|
| `region` | State, or `CENTRAL` for central-government rates |
| `zone` | Area inside the state, where rates differ by place; empty if one rate covers the whole state |
| `sector` | The kind of work the rate is for |
| `skill` | Unskilled, Semi-skilled, Skilled or Highly skilled |
| `daily_rupees` | Rate per day, including any dearness or special allowance |
| `monthly_rupees` | Rate per month where the notice gives a monthly figure. The daily rate is then monthly ÷ 26, the usual rule. |
| `effective_from`, `valid_to` | When the rate applies. `valid_to` is empty when the notice sets no end. |
| `confidence` | **high**: read from the government notice itself. **medium**: part read from the notice, or several summaries agree. **low**: from summaries only, still to be checked. |
| `checked` | How and when it was checked |
| `source`, `url` | The notice, and where to read it |

## Where each state stands (20 November 2026)

| State | Kind of work | Confidence | Notes |
|---|---|---|---|
| Odisha | All scheduled employments | high | VDA notice of 8 Oct 2026, read in Odia |
| Karnataka | All scheduled employments, 3 zones | high | Final notification of 22 May 2026 |
| Maharashtra | Construction, 3 zones | high | Allowance notice for July–December 2026 (item 11). **Due again 1 January 2027.** |
| West Bengal | Factories, workshops, shops, clinics; 2 zones | high | Circular for July–December 2026. Construction has its own schedule, not yet added. **Due again 1 January 2027.** |
| Gujarat | 46 scheduled employments, 2 zones | medium | The October 2026 allowance (₹72.50 a day) was read from the circular. The 2023 basic rates come from summaries. |
| Delhi | All scheduled employments | medium | Summaries agree no revision has followed April 2025 |
| Telangana | All scheduled employments, unskilled, 3 zones | low | June 2026 rates, from summaries. The October 2026 VDA had not been declared on 17 Nov 2026. |
| Andhra Pradesh | Construction, unskilled | low | Basic rate from summaries, plus the October 2026 allowance. The gazette lists index points, not rupees. |
| Tamil Nadu | Construction, unskilled | low | One summary only, and others disagree |
| Kerala | Shops and establishments, lowest grade | low | Construction has its own schedule, not yet added |
| Central | Construction (central-sphere sites), unskilled, 3 areas | medium | Labour Bureau order of 30 Sep 2026 |

For states with zones, the app checks against the lowest zone. That way it never tells a worker
they are underpaid when they might not be. It also tells them the higher city rate.

## Keeping it right

Most states change their allowance twice a year, on 1 April and 1 October; Maharashtra and West
Bengal on 1 January and 1 July. After each change:

1. Get the notice from the state labour department, or the gazette.
2. Change the rows. Fill in `checked`: who checked, against which notice, and the date. Raise
   `confidence` only when the figure was read from the notice itself.
3. From `android/`, run `python tools/make_wage_pack.py ../data/minimum-wages/minimum_wages_in.csv app/src/main/assets/wage_pack_in.json`,
   then `./gradlew :core:test --tests '*WagePack*'`.

**Still to do:** construction rates for West Bengal, Kerala and Tamil Nadu from their own
schedules; Telangana, Andhra Pradesh and Tamil Nadu checked against the G.O.s.
