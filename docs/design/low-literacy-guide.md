# Designing voice apps for people who read little

What building HisaabSaathi taught us. It is for anyone making an app for daily-wage workers,
farmers, domestic workers, or anyone who reads little, in Indian languages. Each rule says what we
did and why. The code is in this repository if you want to see how.

## 1. Speaking comes first; reading and typing are for later

- **One big button on the home screen:** "Tap and speak". It is the largest thing on the screen and
  the same in every language. Typing and photos are smaller buttons beside it.
- **People talk the way they talk.** "Aaj aath ghante kaam kiya, ek sau bees piece, paanch rupaye
  rate, teen sau advance liya" has to work as one sentence. Don't make people answer form fields
  one by one.
- **Understand local words**: *dihadi, peshgi, kharchi, udhar, dadan, mestri, hajira*. English
  words inside Indian sentences too ("is week ka total").
- **Numbers are the hard part.** People say "dedh sau", "dhai hazaar", "teen sau pachaas", or say
  numbers in English inside Odia. Speech recognisers write them in many forms. We wrote a number
  reader for each language and tested it on real speech. Common words that sound like numbers must
  never become amounts.

## 2. Nothing counts until the person says it is right

- **Read back, then save.** After every entry the app shows a simple card and reads it aloud:
  "Work, 120 pieces at 5 rupees, 600 rupees. Advance, 300 rupees. Is this right?" It saves only
  when the person taps "Right, save" or says "haan".
- **Make "wrong" easy.** "Wrong, say again" is as big as "Right, save", and red. Each line on the
  card can be removed or changed on its own.
- **AI may help understand, but it never makes up a number.** Every amount must be one the person
  said. If the model's answer doesn't match the spoken numbers, it is thrown away. Totals are
  plain arithmetic in code.
- **Undo.** A deleted line can be brought back for a few seconds, because a wrong tap must not cost
  a day's record.

## 3. Never leave the person stuck or in silence

- If the app did not understand, it says so aloud and gives an example of what to say. It never
  shows an empty screen or an error code.
- The question the app heard is shown above its answer. People can see whether a wrong answer came
  from mishearing.
- When a photo's amount cannot be read, the app asks the person to type it. It doesn't stop, and
  it doesn't guess.

## 4. Pictures, colours and places that stay the same

- Each kind of line has its own picture and colour, everywhere: work (green, a spanner), advance
  (marigold, a clock), money received, spending (a cart). People learn the colour before the word.
- Four tabs at the bottom, always in the same order: Home, Ledger, Summary, Wage & Help.
- Amounts are big, bold and on the right. The rupee sign always goes with the number.
- Money owed to the person ("still due") is the biggest number on the home card, in red.

## 5. Language: the person chooses, and it applies everywhere

- The first screen asks "Which language?" with each language written in its own script: ଓଡ଼ିଆ,
  తెలుగు, ಕನ್ನಡ, தமிழ், हिन्दी. Don't use flags or English names.
- Don't pick the language from the phone's location. Migrant workers live in one state and speak
  another language. A choice the person made is never changed for them.
- Everything follows that choice: words, voice, numbers spoken aloud, the PDF report, and the kinds
  of work on the wage screen.
- **Give labels room.** Indian scripts need more height and often more width than English. Every
  button must fit two lines. Test every screen in every language, because a cut-off label is easy
  to miss in a language you don't read.

## 6. Ask little, fill in the rest

- Setup asks for a name and a PIN code. The PIN fills in state and district from India Post's
  directory, on the phone. Where one PIN spans two districts, the app offers both and the person
  picks.
- Dropdowns can be searched, and the likely answer sits at the top in bold.
- Questions can be answered by voice. Setup questions are recorded in each language, so they
  sound natural.

## 7. Big targets for working hands

- Buttons are at least 52 dp tall, and the mic button is over 90 dp. Hands on a worksite are tired,
  dusty or wet.
- Dangerous actions (erase everything, delete what I shared) are small, far from everyday buttons,
  and always ask "Are you sure?"

## 8. Consent people can actually hear

- Ask once, plainly, and read it aloud with a "Listen" button. No answer is chosen in advance.
  "No" is as easy as "yes".
- Say what is collected, where it goes, without what (name, phone number), and how to stop it.
- When the question changes what is collected, ask again. Don't stretch an old "yes" over new data.
- Never collect identifying numbers. The app drops anything that looks like a phone number,
  Aadhaar, bank account or UPI id before it is ever kept.

## 9. Trust: the record belongs to the person

- The record is encrypted on the phone. It leaves only when the person shares it, as a PDF or an
  evidence pack for a union or labour office.
- The evidence pack chains every entry with a fingerprint, so anyone can check that nothing was
  changed afterwards. People fear their employer, and many have been burned by loan apps. A
  record they control, which shows it was not tampered with, is the point of the app.
- Never tell someone they are underpaid unless it is certain. The minimum-wage check uses the
  lowest rate that applies anywhere in the state, then tells them the higher city rate. A false
  alarm can cost a worker their job.

## 10. Built for cheap phones and patchy networks

- After one download everything works offline: speech, understanding and voices.
- Downloads happen in the background, can resume, and say how big they are.
- The model runs only when the simple rules are unsure. That keeps the app fast on a 3 GB phone.

## 11. Testing

- Test with the people who will use it, on their phones, on site, with noise. Things that look
  fine in an office (small text, colour alone, long sentences) fail there.
- Keep a set of real sentences in every language, with the right answers. Run it on every change.
  Ours is in `android/core/src/test/resources/`.
- Check every screen in every language with screenshots before a release.
- Accessibility: every picture-only button has a spoken label for screen readers. Warning text
  has at least 7:1 contrast.
