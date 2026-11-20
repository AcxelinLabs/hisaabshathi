<p align="center">
  <img src="docs/brand/banner.png" alt="HisaabSaathi (हिसाब साथी): a wage ledger you speak to, for India's daily-wage workers. Hindi, Odia, Telugu, Kannada, Tamil, English." width="880">
</p>

<p align="center">
  <a href="https://huggingface.co/AcxelinLabs/hisaabsaathi-model/resolve/main/HisaabSaathi.apk"><img src="docs/brand/download-app.png" alt="Download HisaabSaathi: v0.8.0, Android 10+" height="72"></a>
</p>

<p align="center">
  <img src="docs/brand/facts.png" alt="Voice first, no typing · Works offline · Private, stays on the phone · Proof of unpaid wages · Minimum-wage check · 6 Indian languages" width="644">
</p>

A worker taps one button and says what happened, for example *"Today I worked 8 hours,
120 pieces at 5 rupees each, took 300 rupees advance"*. HisaabSaathi writes it down, reads it
back, and saves it once they confirm. Later they ask *"How much did I earn this week?"* or *"How
much does the contractor still owe me?"* and hear the answer. They don't need to read or type.

It works entirely on the phone, with no internet after a one-time download.

<p align="center">
  <img src="docs/screens/app-overview.png" alt="HisaabSaathi: home, confirm, spoken answer, ledger, summary, minimum wage" width="640">
</p>

It needs Android 10 or newer.

## What it does

- **Record by voice:**
  - work: hours, pieces and rate;
  - advances, wages received, and spending by category (food, travel, rent, phone, health,
    sent home);
  - a confirmation card read aloud, saved only when the worker says it's right.
- **Ask by voice:** what was earned, taken as advance, spent, or is still owed, for today,
  yesterday, this or last week, this or last month, or this year. Also the government minimum
  wage for their state. Local words and mixed English work: *dihadi, peshgi, hajira, dadan,
  mestri, "is week ka total"*.
- **Ledger by day:** daily totals; tap to change a line; undo a delete.
- **Summaries and a savings goal**, on screen and read aloud.
- **Minimum-wage check** against the official rates of the worker's state.
- **Proof:**
  - a **PDF report** to share or print;
  - an **evidence pack**: every record with a check showing nothing was changed afterwards,
    for a contractor, labour office, union or NGO when wages are disputed.
- **Bill or advance-slip photo** to amount, read on the phone.
- **Reminders** at 3 PM and 9 PM, and **backup and restore**.

<p align="center">
  <img src="docs/screens/six-languages.png" alt="Home screen in Odia, Telugu, Kannada, Tamil and English" width="880">
</p>

## Private by design

- Records are **encrypted on the phone** (SQLCipher, key held by the Android Keystore) and
  never go to a server or a cloud backup.
- Speech recognition, understanding and the reply voices all run **on the phone**.
- There are no accounts and no tracking. Only the worker sees their records.
- **Help improve HisaabSaathi** is asked once, at setup, and read aloud. Nothing is chosen for
  the worker. Only if they say yes, each time they speak or type to the app, their voice clip,
  what the app heard, and what it understood and answered are sent to the team over Wi-Fi.
  Nothing carries their name or phone number, and anything containing a phone, Aadhaar or bank
  number is never kept. Each upload is encrypted on the phone so that only the team's training
  pipeline can open it. They can turn it off in Settings, which deletes what is still waiting,
  or have everything already sent deleted. What they share stays in the team's Azure account
  in India, from upload to training. The upload service and pipeline are in
  [`server/`](server/README.md).

## How it understands

1. **Speech to text, on the phone:** AI4Bharat IndicConformer for the five Indian languages
   and NVIDIA Parakeet for English, run with sherpa-onnx.
2. **Rules first:** a grammar for numbers and ledger words in all six languages, including
   numbers said in English inside Indian sentences and common mishearings. All arithmetic is
   plain code.
3. **A small on-device language model**, Qwen3-0.6B fine-tuned for this app, runs only when
   the rules are unsure. It labels the numbers that were said, or names the period of a
   question. It never invents an amount: an answer that doesn't match the spoken numbers is
   discarded.
4. **Replies are spoken** with Indic Tiny TTS voices, or the phone's own voice for English.

## Credits and licences

| Part | By | Licence |
|---|---|---|
| IndicConformer speech models | AI4Bharat | MIT |
| Parakeet TDT-CTC 110M | NVIDIA | CC-BY-4.0 |
| Qwen3-0.6B (base of the fine-tuned model) | Alibaba Cloud Qwen team | Apache-2.0 |
| Indic Tiny TTS voices | Sulabh Katiyar, on AI4Bharat Rasa | CC-BY-4.0 |
| Vocos vocoder | Charactr | MIT |
| Silero VAD | Silero | MIT |
| sherpa-onnx | k2-fsa | Apache-2.0 |
| LiteRT-LM | Google | Apache-2.0 |
| ML Kit text recognition | Google | ML Kit terms |
| Anek fonts | Ek Type | OFL |

The fine-tuned model and the English speech model are published at
[huggingface.co/AcxelinLabs/hisaabsaathi-model](https://huggingface.co/AcxelinLabs/hisaabsaathi-model).
<p align="center"><b>HisaabSaathi</b> is built by <b>Acxelin Labs</b>.</p>
