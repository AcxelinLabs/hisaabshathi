# Wage disputes settled with HisaabSaathi records: a log for partners

For unions, legal-aid groups, NGOs and labour offices that help workers with unpaid or short
wages. When a worker brings their HisaabSaathi record (the evidence pack, a PDF report, photos of
slips, or the screen itself) to a dispute, note the case here. Once a month, send the sheet to
the HisaabSaathi team.

This is how we learn whether the app helps workers get paid. It is also one of the measures we
report to our funders and in our monthly public update: disputes settled using app records.

## What to write, and what never to write

**Write:** the partner's own case code, the month, the state and district, the kind of work, what
the dispute was about, the amounts, which part of the record was used, and how it ended.

**Never write:** the worker's name, phone number, Aadhaar, address or photo, or the contractor's or
company's name. A case must not point to a person. Use your own case code (`E-014`) and keep the
key to it in your own files.

Ask the worker first. Write `yes` in the last column only if they agreed that the case, without
their name, may be counted. Leave the case out if they did not.

## The sheet: `dispute-log.csv`

Open it in Excel, Google Sheets or LibreOffice. One row per case. The first row is an example:
delete it.

| Column | What to write |
|---|---|
| `case_code` | Your own code for the case |
| `partner` | Your organisation |
| `state`, `district` | Where the work was |
| `month_opened`, `month_closed` | `2026-11`. Leave `month_closed` empty while the case is open. |
| `work` | construction, brick kiln, factory, farm, domestic, loading, other |
| `issue` | wages not paid, paid less than agreed, below minimum wage, advance wrongly deducted, piece-rate dispute, other |
| `amount_claimed_rupees` | What the worker said they were owed |
| `amount_recovered_rupees` | What they got. Use 0 if nothing, and leave it empty while the case is open. |
| `record_used` | evidence pack, PDF report, photos, screen shown, minimum-wage screen. Separate several with `;`. |
| `outcome` | settled in full, settled in part, not settled, still open, sent to labour office, sent to court |
| `how_the_record_helped` | One sentence, no names. Example: "The dated entries showed 6 unpaid days". |
| `worker_agreed_to_share` | yes |

## Each month

1. Add the new cases and update the open ones.
2. Send the sheet to the HisaabSaathi team by the 5th of the next month.
3. We add the numbers up across partners: cases, amounts recovered, and which records helped. We
   publish only totals, never single cases, in the monthly update
   ([`../updates/`](../updates/README.md)).

## Telling us what to change

If the record did not help, or a labour officer asked for something it lacks, write it in
`how_the_record_helped` or tell the team. Changes to the evidence pack come from exactly this.
