# How a daily-wage worker finds HisaabSaathi: audit, November 2026

We walked through every screen of 0.9.5 as a construction worker would: someone who reads little,
is tired at the end of a shift, has a cheap phone, little data, and patience for one thing,
writing down today's money. For each problem: what they hit, why it matters, and what we changed.
The changes are on `Qubit-Man-workspace`; they reach testers in the next build.

Severity: **P0** stops the worker; **P1** makes them struggle or give up; **P2** slows them;
**P3** polish.

## The biggest problem: too much before the first entry

0.9.5 asked **eight screens** of questions before the worker could record anything:
- language;
- "fill it in myself or scan Aadhaar";
- name;
- age;
- PIN, with seven controls on one screen;
- selfie;
- a 616 MB "smart model" download decision;
- a ten-line consent question.

Most workers would quit there, or tap through without reading. Nothing on those screens is
needed to write down a day's wage.

## Findings

| # | Sev | Where | What the worker hits | Change |
|---|---|---|---|---|
| 1 | P0 | Setup | Eight screens before the first entry | **Three screens:** language, name, PIN, then Home. Age and photo are on the profile page. Aadhaar is one quiet "fill from Aadhaar" link on the name screen. |
| 2 | P0 | Setup | A ten-line consent question, before they know what the app is | **Asked once, on Home, after 3 days and 3 saved lines.** One line saying what is sent, "Read all" and Listen for the whole text, Yes and No equally easy, ✕ for later (a week). Still a real yes: voice is never sent without it. |
| 3 | P0 | Setup | "Download 616 MB?": a worker cannot judge megabytes, and on prepaid data it costs money | **No question.** The smart model downloads by itself, on Wi-Fi only. On mobile data it downloads only if the worker taps Download in Settings. |
| 4 | P1 | PIN screen | Help paragraph, location button and its note, PIN box, mic, status line, state list, district list | **The PIN box and one line:** "✓ Khordha, Odisha". State and district appear only when the PIN doesn't settle them, or when the worker taps Change or "No PIN?". "Use my location" is a quiet link. |
| 5 | P1 | Confirm | Every line had two more buttons (Delete, Change). The worker's words sat under a small "You said" label in a tinted box. The two most important buttons had 13 sp captions. | **The whole line is the button;** delete is inside it. The worker's words are quiet grey text. "Wrong, say again" and "Right, save" have 16 sp labels beside their icons. |
| 6 | P1 | Tabs | A star for Summary and an "i" for Wage & help say nothing | A **bar chart** and a **rupee**. |
| 7 | P1 | Wage | Notice numbers, dates, a legal warning box and a big lone number, all at once | **Rates first.** Source and dates one tap away. Below, one row of the worker's own average and one verdict in green or red. If setup skipped the state, it is asked right here. |
| 8 | P1 | Settings | "Hindi speech (AI4Bharat IndicConformer) · 199 MB · MIT; ONNX export Apache-2.0", a debug log and credits, mixed in with the settings people change | **On top:** language, work state, reminders, Help improve (with one line on what is sent). **Under "More options":** downloads (named by what they do: "To understand your voice"), backup, privacy, credits, erase. |
| 9 | P1 | Summary | A bar chart repeating the numbers just above it, and an empty "savings goal" card | **No chart.** Days worked moves onto the receipt. The goal is one link until one is set. |
| 10 | P2 | Home | ₹0 rows for advance and money received, "Saved" stays until closed, the example sentence shows forever | **Only what happened** shows. "Saved" goes by itself after 3 seconds. The example sentence shows until there are 3 lines. |
| 11 | P2 | Confirm | "हाँ / नहीं" with a mic: it isn't clear this means "say yes or no aloud" | **Labelled "Say yes or no".** *Next:* listen for the answer automatically after the read-back. |
| 12 | P2 | Setup | A green ▶ beside each question looks like "play a video" | **A speaker icon,** the same everywhere something is read aloud. |
| 13 | P2 | Tabs | The tab "हिसाब" and the app "हिसाब साथी" share a word | *Next:* name the tab "कुल" (totals) in Hindi, and check the other languages with workers. |
| 14 | P3 | Everywhere | Light theme only | Fine for outdoor use in daylight. Revisit if testers use the app at night. |

## Second pass (0.9.6)

We went through the screenshots of the redesigned app again, screen by screen:

| # | Sev | Where | What we found | Change |
|---|---|---|---|---|
| 15 | P0 | Wage | The 30-day average "₹600" broke into a column of single characters: the long label pushed it aside | **The amount never wraps.** A label takes at most 60% of a row and wraps inside it; this holds on every receipt row in the app. |
| 16 | P1 | Setup | A large grey "Tap and say your answer" button while the voice download is still going. It looks broken. | **The button appears only once speech works.** Until then, typing is the only way, and that is clear. |
| 17 | P1 | Everywhere | Some buttons were round pills (Material's default), others had 10 dp corners | **One shape:** every filled and outlined button has 10 dp corners. |
| 18 | P2 | Summary | Period choices were Material filter chips, the look of every template | **Ledger-book tabs:** today, week, month, year, with the chosen one underlined in marigold. |
| 19 | P2 | Summary | The receipt was titled "Week" right under the "Week" tab | **Title removed:** the tab says it. |
| 20 | P2 | Profile | The privacy sentence sat in a yellow warning box | **A plain line with a lock icon:** it reassures, it does not alarm. |

## Why it looked generic

The things that made the app look like a template rather than a ledger built for one job:
- **Small labels above content.** "You said", "Your average daily earning (last 30 days)" sat
  over the thing itself. Removed; the content speaks.
- **Tinted boxes with an icon on every message.** Yellow warning boxes and green quote boxes.
  Kept only where something needs action; plain text elsewhere.
- **Decorative charts.** Bars that repeated the numbers beside them. Removed.
- **Meaningless icons.** A star and an "i". Replaced with pictures of what is behind the tab.
- **Equal-weight buttons everywhere.** Two or three pill buttons per card. Now one action per
  card, and the card itself is the button.

What stays, because it is the app's own look: the ledger-green and marigold, the receipt that
tears at the bottom, the dotted lines leading to each amount, and one big marigold mic.

## Technical check (Android)

| Area | Score /4 | Notes |
|---|---|---|
| Accessibility | 3 | Text in sp; touch targets 48 dp or more (mic 90 dp+); picture-only buttons have spoken labels; warning text 7:1. Still to do: a TalkBack pass on a real phone. |
| Performance | 3 | The ledger is a lazy list; photos are decoded small for thumbnails; heavy models load only when needed. |
| Colours and theme | 2 | Colours are named tokens in one place, but there is no dark theme and a few screens use white directly. |
| Android conformance | 3 | Material 3 components, system Back works everywhere, edge-to-edge with insets. |
| Screen sizes | 2 | Built for phones. Content is capped at 560 dp wide, but there is no tablet layout, and landscape is not tested. |
| **Total** | **13/20** | Acceptable. Fine for phones; tablets and dark mode are not targets yet. |

## How we checked

- Screenshots of every screen in all six languages, taken by the app's tests (`android/app/build/screens/`).
- Each fix is covered by a test: the three-step setup, the PIN that needs no district box, the
  Help improve card appearing only after a few days, and the line that opens on a tap.
- **Still needed:** watching five workers use it, on their phones, at a site. A screen review cannot
  replace that.
