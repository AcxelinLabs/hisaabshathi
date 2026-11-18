#!/usr/bin/env python3
"""Ledger questions in every app language, for teaching and testing the model's second job:
when the rules do not understand a sentence, is it a question about the worker's own
records, and which period and topic?

TRAIN and TEST use different phrasings (the period words are shared vocabulary), so the
test measures new ways of asking, not memorised sentences. NONE sentences are everyday
questions and requests that are not about the ledger. All text is best-effort and needs
review by native speakers.

usage (from android/): make_questions.py   -> writes core/src/test/resources/questions_heldout.jsonl
"""
import json, random

PERIODS = ["DAY", "WEEK", "MONTH", "YEAR"]

# lang: P = period said as an adverb ("this week"), G = as a modifier ("this week's"),
# train/test = (template, topic); {p}/{g} absent means no period was said (WEEK).
L = {
    "hi": dict(
        P=["आज", "इस हफ्ते", "इस महीने", "इस साल"], G=["आज का", "इस हफ्ते का", "इस महीने का", "इस साल का"],
        train=[("{p} मेरी कमाई कितनी हुई", "EARNED"), ("{p} मैंने कितना कमाया", "EARNED"), ("{p} कितना एडवांस लिया", "ADVANCE"),
               ("{p} ठेकेदार से कितने पैसे उठाए", "ADVANCE"), ("{p} मेरा खर्चा कितना हुआ", "EXPENSE"), ("{p} कितने रुपये खर्च हुए", "EXPENSE"),
               ("{p} कितनी मजदूरी बाकी है", "DUE"), ("सेठ पर {g} कितना बकाया है", "DUE"), ("{g} पूरा हिसाब बताओ", "ALL"), ("{g} हिसाब दिखाओ", "ALL")],
        test=[("{g} कमाई बताना", "EARNED"), ("{p} काम से हाथ में कितना पैसा आया", "EARNED"), ("{p} मालिक से उधार कितना उठाया", "ADVANCE"),
              ("{p} पैसे कहाँ कहाँ लगे", "EXPENSE"), ("ठेकेदार से अभी कितना लेना है", "DUE"), ("क्या {g} पूरी मजदूरी मिल गई", "DUE"), ("{g} सारा लेखा जोखा", "ALL")],
        none_train=["आज मौसम कैसा है", "मुझे गाना सुनाओ", "मेरा फोन चार्ज नहीं हो रहा", "कल बाजार जाना है", "खाना बन गया क्या"],
        none_test=["बस कितने बजे आएगी", "मेरे बेटे का स्कूल कब खुलेगा", "पानी कितना गरम है"],
    ),
    "hi-Latn": dict(
        P=["aaj", "is hafte", "is mahine", "is saal"], G=["aaj ka", "is hafte ka", "is mahine ka", "is saal ka"],
        train=[("{p} meri kamai kitni hui", "EARNED"), ("{p} maine kitna kamaya", "EARNED"), ("{p} kitna advance liya", "ADVANCE"),
               ("{p} kharcha kitna hua", "EXPENSE"), ("{p} kitni mazdoori baaki hai", "DUE"), ("{g} hisaab batao", "ALL")],
        test=[("{g} kamai batao na", "EARNED"), ("{p} udhaar kitna uthaya", "ADVANCE"), ("{p} paise kahan gaye", "EXPENSE"),
              ("seth se abhi kitna lena hai", "DUE"), ("{g} pura lekha jokha", "ALL")],
        none_train=["gaana sunao", "mausam kaisa hai", "phone charge nahi ho raha"],
        none_test=["bus kitne baje aayegi", "school kab khulega"],
    ),
    "en": dict(
        P=["today", "this week", "this month", "this year"], G=["today", "this week", "this month", "this year"],
        train=[("how much did I earn {p}", "EARNED"), ("what did I make {p}", "EARNED"), ("how much advance did I take {p}", "ADVANCE"),
               ("how much did I borrow from the contractor {p}", "ADVANCE"), ("how much did I spend {p}", "EXPENSE"), ("what were my expenses {p}", "EXPENSE"),
               ("how much is still owed to me {p}", "DUE"), ("what is my balance {p}", "DUE"), ("show me everything for {g}", "ALL"), ("give me the summary for {g}", "ALL")],
        test=[("{p} what was my income", "EARNED"), ("money taken in advance {p}", "ADVANCE"), ("where did my money go {p}", "EXPENSE"),
              ("does the boss still owe me anything", "DUE"), ("did I get all my wages {p}", "DUE"), ("full picture for {g} please", "ALL")],
        none_train=["what is the weather", "tell me a joke", "my phone is not charging", "I have to go to the market tomorrow"],
        none_test=["when does the bus come", "how hot is the water", "what time is it"],
    ),
    "or": dict(
        P=["ଆଜି", "ଏହି ସପ୍ତାହରେ", "ଏହି ମାସରେ", "ଏହି ବର୍ଷରେ"], G=["ଆଜିର", "ଏହି ସପ୍ତାହର", "ଏହି ମାସର", "ଏହି ବର୍ଷର"],
        train=[("{p} ମୋର କେତେ ରୋଜଗାର ହେଲା", "EARNED"), ("{p} ମୁଁ କେତେ କମାଇଲି", "EARNED"), ("{p} କେତେ ଆଡଭାନ୍ସ ନେଲି", "ADVANCE"),
               ("{p} ଠିକାଦାରଙ୍କଠାରୁ କେତେ ଟଙ୍କା ନେଲି", "ADVANCE"), ("{p} କେତେ ଖର୍ଚ୍ଚ ହେଲା", "EXPENSE"), ("{p} ମୋର ଖର୍ଚ୍ଚ କେତେ", "EXPENSE"),
               ("{p} କେତେ ମଜୁରି ବାକି ଅଛି", "DUE"), ("{p} ମାଲିକ ପାଖରେ କେତେ ବାକି", "DUE"), ("{g} ସବୁ ହିସାବ କୁହ", "ALL"), ("{g} ହିସାବ ଦେଖାଅ", "ALL")],
        test=[("{p} କାମରୁ କେତେ ପଇସା ଆସିଲା", "EARNED"), ("{p} ଧାର କେତେ ଉଠାଇଲି", "ADVANCE"), ("{p} ପଇସା କେଉଁଠି ଗଲା", "EXPENSE"),
              ("ଠିକାଦାରଙ୍କଠୁ ଆଉ କେତେ ପାଇବାକୁ ଅଛି", "DUE"), ("{p} ମୋ ପୁରା ମଜୁରି ମିଳିଗଲା କି", "DUE"), ("{g} ପୁରା ଲେଖାଜୋଖା", "ALL")],
        none_train=["ଆଜି ପାଣିପାଗ କେମିତି", "ଗୀତ ଶୁଣାଅ", "ମୋ ଫୋନ୍ ଚାର୍ଜ ହେଉନି", "କାଲି ବଜାର ଯିବାକୁ ଅଛି"],
        none_test=["ବସ୍ କେତେବେଳେ ଆସିବ", "ସ୍କୁଲ୍ କେବେ ଖୋଲିବ", "ପାଣି କେତେ ଗରମ"],
    ),
    "te": dict(
        P=["ఈరోజు", "ఈ వారం", "ఈ నెల", "ఈ సంవత్సరం"], G=["ఈరోజు", "ఈ వారం", "ఈ నెల", "ఈ సంవత్సరం"],
        train=[("{p} నేను ఎంత సంపాదించాను", "EARNED"), ("{p} నా కూలి ఎంత వచ్చింది", "EARNED"), ("{p} ఎంత అడ్వాన్స్ తీసుకున్నాను", "ADVANCE"),
               ("{p} మేస్త్రి దగ్గర ఎంత డబ్బు తీసుకున్నాను", "ADVANCE"), ("{p} ఎంత ఖర్చు అయింది", "EXPENSE"), ("{p} నా ఖర్చులు ఎంత", "EXPENSE"),
               ("{p} ఇంకా ఎంత బాకీ ఉంది", "DUE"), ("{p} ఓనర్ నాకు ఎంత ఇవ్వాలి", "DUE"), ("{g} మొత్తం లెక్క చెప్పు", "ALL"), ("{g} లెక్క చూపించు", "ALL")],
        test=[("{p} పని చేసి ఎంత డబ్బు వచ్చింది", "EARNED"), ("{p} అప్పు ఎంత తీసుకున్నాను", "ADVANCE"), ("{p} డబ్బులు ఎక్కడ పోయాయి", "EXPENSE"),
              ("మేస్త్రి నుండి ఇంకా ఎంత రావాలి", "DUE"), ("{p} నా పూర్తి కూలి వచ్చిందా", "DUE"), ("{g} పూర్తి వివరాలు", "ALL")],
        none_train=["ఈరోజు వాతావరణం ఎలా ఉంది", "పాట వినిపించు", "నా ఫోన్ ఛార్జ్ అవ్వట్లేదు", "రేపు మార్కెట్ కి వెళ్ళాలి"],
        none_test=["బస్సు ఎప్పుడు వస్తుంది", "స్కూల్ ఎప్పుడు తెరుస్తారు", "నీళ్ళు ఎంత వేడిగా ఉన్నాయి"],
    ),
    "kn": dict(
        P=["ಇವತ್ತು", "ಈ ವಾರ", "ಈ ತಿಂಗಳು", "ಈ ವರ್ಷ"], G=["ಇವತ್ತಿನ", "ಈ ವಾರದ", "ಈ ತಿಂಗಳ", "ಈ ವರ್ಷದ"],
        train=[("{p} ನಾನು ಎಷ್ಟು ದುಡಿದೆ", "EARNED"), ("{p} ನನ್ನ ಕೂಲಿ ಎಷ್ಟು ಬಂತು", "EARNED"), ("{p} ಎಷ್ಟು ಅಡ್ವಾನ್ಸ್ ತಗೊಂಡೆ", "ADVANCE"),
               ("{p} ಮೇಸ್ತ್ರಿ ಹತ್ರ ಎಷ್ಟು ದುಡ್ಡು ತಗೊಂಡೆ", "ADVANCE"), ("{p} ಎಷ್ಟು ಖರ್ಚಾಯ್ತು", "EXPENSE"), ("{p} ನನ್ನ ಖರ್ಚು ಎಷ್ಟು", "EXPENSE"),
               ("{p} ಇನ್ನೂ ಎಷ್ಟು ಬಾಕಿ ಇದೆ", "DUE"), ("{p} ಓನರ್ ನನಗೆ ಎಷ್ಟು ಕೊಡಬೇಕು", "DUE"), ("{g} ಪೂರ್ತಿ ಲೆಕ್ಕ ಹೇಳು", "ALL"), ("{g} ಲೆಕ್ಕ ತೋರಿಸು", "ALL")],
        test=[("{p} ಕೆಲಸದಿಂದ ಎಷ್ಟು ದುಡ್ಡು ಬಂತು", "EARNED"), ("{p} ಸಾಲ ಎಷ್ಟು ತಗೊಂಡೆ", "ADVANCE"), ("{p} ದುಡ್ಡು ಎಲ್ಲಿ ಹೋಯ್ತು", "EXPENSE"),
              ("ಮೇಸ್ತ್ರಿಯಿಂದ ಇನ್ನೂ ಎಷ್ಟು ಬರಬೇಕು", "DUE"), ("{p} ನನ್ನ ಪೂರ್ತಿ ಕೂಲಿ ಸಿಕ್ತಾ", "DUE"), ("{g} ಎಲ್ಲಾ ವಿವರ", "ALL")],
        none_train=["ಇವತ್ತು ಹವಾಮಾನ ಹೇಗಿದೆ", "ಹಾಡು ಹಾಕು", "ನನ್ನ ಫೋನ್ ಚಾರ್ಜ್ ಆಗ್ತಿಲ್ಲ", "ನಾಳೆ ಮಾರ್ಕೆಟ್ ಹೋಗಬೇಕು"],
        none_test=["ಬಸ್ ಯಾವಾಗ ಬರುತ್ತೆ", "ಶಾಲೆ ಯಾವಾಗ ಶುರು", "ನೀರು ಎಷ್ಟು ಬಿಸಿ ಇದೆ"],
    ),
    "ta": dict(
        P=["இன்னைக்கு", "இந்த வாரம்", "இந்த மாசம்", "இந்த வருஷம்"], G=["இன்னைக்கு", "இந்த வார", "இந்த மாச", "இந்த வருஷ"],
        train=[("{p} நான் எவ்வளவு சம்பாதிச்சேன்", "EARNED"), ("{p} என் கூலி எவ்வளவு வந்தது", "EARNED"), ("{p} எவ்வளவு அட்வான்ஸ் வாங்கினேன்", "ADVANCE"),
               ("{p} மேஸ்திரிகிட்ட எவ்வளவு பணம் வாங்கினேன்", "ADVANCE"), ("{p} எவ்வளவு செலவாச்சு", "EXPENSE"), ("{p} என் செலவு எவ்வளவு", "EXPENSE"),
               ("{p} இன்னும் எவ்வளவு பாக்கி இருக்கு", "DUE"), ("{p} முதலாளி எனக்கு எவ்வளவு தரணும்", "DUE"), ("{g} முழு கணக்கு சொல்லு", "ALL"), ("{g} கணக்கு காட்டு", "ALL")],
        test=[("{p} வேலை செஞ்சு எவ்வளவு காசு வந்தது", "EARNED"), ("{p} கடன் எவ்வளவு வாங்கினேன்", "ADVANCE"), ("{p} காசு எங்க போச்சு", "EXPENSE"),
              ("மேஸ்திரிகிட்ட இருந்து இன்னும் எவ்வளவு வரணும்", "DUE"), ("{p} என் முழு கூலி கிடைச்சுதா", "DUE"), ("{g} எல்லா விவரமும்", "ALL")],
        none_train=["இன்னைக்கு வானிலை எப்படி", "பாட்டு போடு", "என் போன் சார்ஜ் ஆகல", "நாளைக்கு மார்க்கெட் போகணும்"],
        none_test=["பஸ் எப்போ வரும்", "ஸ்கூல் எப்போ திறக்கும்", "தண்ணி எவ்வளவு சூடா இருக்கு"],
    ),
}


def expand(lang, which):
    """(text, expected) pairs: expected is 'PERIOD TOPIC' or 'NONE'."""
    d = L[lang]; out = []
    for tpl, topic in d[which]:
        if "{p}" in tpl or "{g}" in tpl:
            for i, per in enumerate(PERIODS):
                out.append((tpl.format(p=d["P"][i], g=d["G"][i]), f"{per} {topic}"))
        else:
            out.append((tpl, f"WEEK {topic}"))
    out += [(t, "NONE") for t in d["none_" + which]]
    return out


def answer(expected):
    """The model's target answer, in the format Slm.parseQuestion reads."""
    if expected == "NONE":
        return "none"
    per, topic = expected.split()
    return f"period={dict(DAY='today', WEEK='week', MONTH='month', YEAR='year')[per]}\ntopic={topic.lower()}"


def train_examples(n, seed=5):
    rng = random.Random(seed)
    pool = [(lang, t, e) for lang in L for t, e in expand(lang, "train")]
    return [rng.choice(pool) for _ in range(n)]


if __name__ == "__main__":
    rows = [{"lang": lang, "text": t, "expect": e} for lang in L for t, e in expand(lang, "test")]
    with open("core/src/test/resources/questions_heldout.jsonl", "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(len(rows), "held-out questions;", sum(len(expand(l, "train")) for l in L), "training sentences")
