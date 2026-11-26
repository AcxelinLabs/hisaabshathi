# HisaabSaathi: rules for anyone working on it

## Who it is for

India's daily-wage and piece-rate workers: construction, brick kilns, loading, farms, domestic
work. Many read little or not at all. They have cheap Android phones, prepaid data and patchy
networks, and they are tired at the end of a shift. Every decision is checked against them, not
against a typical app user.

## Voice is the main way the app and the worker talk

Typing is always secondary.

- **The app speaks first and then listens.** Every question it asks (setup, confirmations) is
  said aloud, and the app listens for the answer straight after.
- **Everything the app understood is read back.** It is confirmed by saying "yes" or "no".
- **The mic is the biggest control on a screen where the worker gives input.** Typing is a
  smaller fallback below it, never the only way and never the first.
- **Design a new feature's spoken flow first.** Then add the screen and, last, the typing path.
- **Answers are short and spoken in the worker's language,** with numbers said the way people
  say them.

## Keep it simple

- One main action per screen, few words, and pictures and colours that mean the same everywhere.
- Defaults instead of questions: setup is language, name and PIN; everything else waits.
- No tables of figures for workers to read. If they need a fact, they ask and the app says it.

## Language

The worker's choice, used everywhere, in Hindi, Odia, Telugu, Kannada, Tamil and English. Every new
string needs all six. `core` tests fail on a missing translation.

## Privacy

- **"Help improve" is on by default (team decision, 0.9.12).** Voice clips of each interaction
  (silences cut out) and what the app understood go to the team's Azure account over Wi-Fi.
  The worker is told once, aloud and on a card on the first Home, and can turn it off there or in
  Settings at any time; turning it off deletes what is waiting. Nothing else leaves the phone.
- **Setup answers (name, PIN) are heard on the phone only,** never by an online recogniser.
- **The worker's name, and phone, Aadhaar, bank and UPI numbers or emails, are never kept or sent,**
  voice clip included. What workers share stays in the
  team's Azure account in India (`server/`).

## Working in this repo

- Android app: `android/` (`./gradlew test` before every commit; screenshots land in
  `android/app/build/screens/`). Upload service and pipeline: `server/`.
- Commits are authored as QubitMan, with no co-author lines.
