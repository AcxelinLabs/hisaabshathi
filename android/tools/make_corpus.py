#!/usr/bin/env python3
"""Builds core/src/test/resources/corpus.jsonl: several hundred test utterances in six
languages (plus Hindi in Latin letters) with the result the app must produce.

Entry sentences come from hand-written templates filled with spoken number words taken
from the app's own number tables, once as words (what speech recognition produces) and
once as digits (what a person types). Greetings, simple questions and out-of-scope
requests are written by hand. Run from the android/ directory:  python3 tools/make_corpus.py
"""
import itertools, json, random, re

random.seed(7)
CORE = "core/src/main/kotlin/com/acxelin/hisaabsaathi/core/"

def rows(text):
    out = {}
    for item in re.split(r"[;]", text):
        if "=" not in item: continue
        v, words = item.split("=", 1)
        try: v = float(v.strip())
        except ValueError: continue
        out.setdefault(v, []).extend(w.strip() for w in words.split("|") if w.strip())
    return out

def kotlin_block(src, name):
    i = src.index(name)
    j = src.index(")", src.index("(", i))
    return " ; ".join(re.findall(r'"([^"]*)"', src[i:j]))

nw = open(CORE + "NumberWords.kt", encoding="utf-8").read()
TABLE = {"hi": rows(kotlin_block(nw, "private val hindi = table(")), "or": rows(kotlin_block(nw, "private val odia = table(")),
         "en": rows(kotlin_block(nw, "private val english = table("))}
MULT = {"hi": {100: "सौ", 1000: "हजार"}, "or": {100: "ଶହ", 1000: "ହଜାର"}, "en": {100: "hundred", 1000: "thousand"}}
for code, f in (("te", "Te"), ("kn", "Kn"), ("ta", "Ta")):
    src = open(CORE + f"i18n/{f}.kt", encoding="utf-8").read()
    body = src[src.index("val numbers"):src.index("val fractionPrefix")]
    TABLE[code] = rows(" ; ".join(re.findall(r'"([^"]*)"', body[:body.index("val multipliers")])))
    m = rows(" ; ".join(re.findall(r'"([^"]*)"', body[body.index("val multipliers"):])))
    MULT[code] = {int(k): v[0] for k, v in m.items()}

def word(lang, n):
    """Spoken form of n (1..9999) built from the app's tables."""
    t = TABLE[lang]
    def pick(v): return random.choice(t[float(v)][:2])
    if float(n) in t: return pick(n)
    if n < 100:  # English only: "twenty five"
        return pick(n - n % 10) + " " + pick(n % 10)
    parts = []
    th, rest = divmod(n, 1000)
    if th:
        parts.append(pick(th * 1000) if float(th * 1000) in t else f"{word(lang, th)} {MULT[lang][1000]}")
    h, r = divmod(rest, 100)
    if h:
        parts.append(pick(h * 100) if float(h * 100) in t else f"{word(lang, h)} {MULT[lang][100]}")
    if r: parts.append(word(lang, r))
    return " ".join(parts)

T = {
 "hi": dict(piece="आज {H} घंटे काम किया {P} पीस {R} रुपये रेट {A} एडवांस लिया", wage_recv="आज {W} रुपये मजदूरी मिली", wage_unpaid="आज की दिहाड़ी {W} रुपये",
    exp_food="सब्जी पर {E} रुपये खर्च किए", adv="ठेकेदार से {A} रुपये एडवांस लिया", yday="कल {H} घंटे काम किया {W} रुपये मजदूरी", hourly="{H} घंटे काम रेट {HR} रुपये",
    exp_travel="बस में {E} रुपये खर्च हुए", exp_health="दवा पर {E} रुपये खर्च किए",
    q=[("इस हफ्ते कितना कमाया", "WEEK", "EARNED"), ("इस महीने कितना एडवांस लिया", "MONTH", "ADVANCE"), ("आज कितना बाकी है", "DAY", "DUE"), ("इस महीने कितना खर्च हुआ", "MONTH", "EXPENSE"), ("इस साल कितनी कमाई हुई", "YEAR", "EARNED")]),
 "or": dict(piece="ଆଜି {H} ଘଣ୍ଟା କାମ କଲି {P} ପିସ୍ {R} ଟଙ୍କା ଦର {A} ଟଙ୍କା ଆଡଭାନ୍ସ ନେଲି", wage_recv="ଆଜି {W} ଟଙ୍କା ମଜୁରୀ ମିଳିଲା", wage_unpaid="ଆଜି {W} ଟଙ୍କା ମଜୁରୀ",
    exp_food="ପରିବା ପାଇଁ {E} ଟଙ୍କା ଖର୍ଚ୍ଚ କଲି", adv="ଠିକାଦାର ଠାରୁ {A} ଟଙ୍କା ଆଡଭାନ୍ସ ନେଲି", yday="ଗତକାଲି {H} ଘଣ୍ଟା କାମ କଲି {W} ଟଙ୍କା ମଜୁରୀ", hourly="{H} ଘଣ୍ଟା କାମ ଦର {HR} ଟଙ୍କା",
    exp_travel="ବସ୍ ଭଡ଼ା {E} ଟଙ୍କା ଖର୍ଚ୍ଚ", exp_health="ଔଷଧ ପାଇଁ {E} ଟଙ୍କା ଖର୍ଚ୍ଚ କଲି",
    q=[("ଏହି ସପ୍ତାହରେ କେତେ ରୋଜଗାର ହେଲା", "WEEK", "EARNED"), ("ଏହି ମାସରେ କେତେ ଆଡଭାନ୍ସ ନେଲି", "MONTH", "ADVANCE"), ("ଏହି ମାସରେ କେତେ ଖର୍ଚ୍ଚ ହେଲା", "MONTH", "EXPENSE"), ("ଆଜି କେତେ ବାକି ଅଛି", "DAY", "DUE")]),
 "en": dict(piece="today I worked {H} hours {P} pieces at {R} rupees and took {A} rupees advance", wage_recv="got {W} rupees wages today", wage_unpaid="today's wage is {W} rupees",
    exp_food="spent {E} rupees on food", adv="took {A} rupees advance from the contractor", yday="yesterday worked {H} hours wage {W} rupees", hourly="{H} hours work rate {HR} rupees",
    exp_travel="spent {E} rupees on bus ticket", exp_health="spent {E} rupees on medicine",
    q=[("how much did I earn this week", "WEEK", "EARNED"), ("how much advance this month", "MONTH", "ADVANCE"), ("how much is due today", "DAY", "DUE"), ("how much did I spend this month", "MONTH", "EXPENSE")]),
 "te": dict(piece="ఈరోజు {H} గంటలు పని {P} పీసులు పీసుకు {R} రూపాయలు {A} అడ్వాన్స్ తీసుకున్నాను", wage_recv="ఈరోజు కూలి {W} రూపాయలు వచ్చింది", wage_unpaid="ఈరోజు పని చేశాను కూలి {W} ఇంకా ఇవ్వలేదు",
    exp_food="కూరగాయలకు {E} రూపాయలు ఖర్చు అయింది", adv="కాంట్రాక్టర్ దగ్గర {A} రూపాయలు అడ్వాన్స్ తీసుకున్నాను", yday="నిన్న {H} గంటలు పని చేశాను {W} కూలి", hourly="ఈరోజు {H} గంటలు పని గంటకు {HR} రూపాయలు",
    exp_travel="బస్సు చార్జీ {E} రూపాయలు ఖర్చు", exp_health="మందులకు {E} రూపాయలు ఖర్చు అయింది",
    q=[("ఈ వారం ఎంత సంపాదించాను", "WEEK", "EARNED"), ("ఈ నెల అడ్వాన్స్ ఎంత తీసుకున్నాను", "MONTH", "ADVANCE")]),
 "kn": dict(piece="ಇವತ್ತು {H} ಗಂಟೆ ಕೆಲಸ {P} ಪೀಸ್ ಪೀಸ್‌ಗೆ {R} ರೂಪಾಯಿ {A} ಅಡ್ವಾನ್ಸ್ ತಗೊಂಡೆ", wage_recv="ಇವತ್ತು ಕೂಲಿ {W} ರೂಪಾಯಿ ಸಿಕ್ತು", wage_unpaid="ಇವತ್ತು ಕೆಲಸ ಮಾಡಿದೆ ಕೂಲಿ {W} ರೂಪಾಯಿ ಇನ್ನೂ ಕೊಟ್ಟಿಲ್ಲ",
    exp_food="ತರಕಾರಿಗೆ {E} ರೂಪಾಯಿ ಖರ್ಚಾಯ್ತು", adv="ಮೇಸ್ತ್ರಿ ಹತ್ರ {A} ರೂಪಾಯಿ ಅಡ್ವಾನ್ಸ್ ತಗೊಂಡೆ", yday="ನಿನ್ನೆ {H} ಗಂಟೆ ಕೆಲಸ ಮಾಡಿದೆ ಕೂಲಿ {W} ರೂಪಾಯಿ", hourly="ಇವತ್ತು {H} ಗಂಟೆ ಕೆಲಸ ಗಂಟೆಗೆ {HR} ರೂಪಾಯಿ",
    exp_travel="ಬಸ್ ಚಾರ್ಜ್ {E} ರೂಪಾಯಿ ಕೊಟ್ಟೆ", exp_health="ಔಷಧಿಗೆ {E} ರೂಪಾಯಿ ಖರ್ಚಾಯ್ತು",
    q=[("ಈ ವಾರ ನಾನು ಎಷ್ಟು ದುಡಿದೆ", "WEEK", "EARNED"), ("ಈ ತಿಂಗಳು ಎಷ್ಟು ಅಡ್ವಾನ್ಸ್ ತಗೊಂಡಿದ್ದೀನಿ", "MONTH", "ADVANCE")]),
 "ta": dict(piece="இன்னைக்கு {H} மணி நேரம் வேலை {P} பீஸ் பீஸுக்கு {R} ரூபாய் {A} ரூபாய் அட்வான்ஸ் வாங்கினேன்", wage_recv="இன்னைக்கு கூலி {W} ரூபாய் கிடைச்சது", wage_unpaid="இன்னைக்கு வேலை செஞ்சேன் சம்பளம் {W} ரூபாய் இன்னும் கொடுக்கல",
    exp_food="காய்கறி வாங்க {E} ரூபாய் செலவாச்சு", adv="கான்ட்ராக்டர் கிட்ட {A} ரூபாய் அட்வான்ஸ் வாங்கினேன்", yday="நேத்து {H} மணி நேரம் வேலை செஞ்சேன் {W} ரூபாய் கூலி", hourly="இன்னைக்கு {H} மணி நேரம் வேலை மணிக்கு {HR} ரூபாய்",
    exp_travel="பஸ்ஸுக்கு {E} ரூபாய் செலவு", exp_health="மருந்துக்கு {E} ரூபாய் செலவு",
    q=[("இந்த வாரம் எவ்வளவு சம்பாதிச்சேன்", "WEEK", "EARNED"), ("இந்த மாசம் எவ்வளவு அட்வான்ஸ் வாங்கினேன்", "MONTH", "ADVANCE")]),
 "hi-Latn": dict(piece="aaj {H} ghante kaam kiya {P} piece {R} rupaye rate {A} advance liya", wage_recv="aaj {W} rupaye majdoori mili", wage_unpaid="aaj ki dihadi {W} rupaye",
    exp_food="sabzi par {E} rupaye kharch", adv="thekedar se {A} rupaye advance liya", yday="kal {H} ghante kaam {W} rupaye dihadi", hourly="{H} ghante kaam rate {HR} rupaye",
    exp_travel="bus ticket {E} rupaye kharch", exp_health="dawa par {E} rupaye kharch",
    q=[("is hafte kitna kamaya", "WEEK", "EARNED"), ("is mahine kitna advance liya", "MONTH", "ADVANCE"), ("aaj kitna baaki hai", "DAY", "DUE")]),
}

CHAT = {
 "hi": [("आपका नाम क्या है", "NAME"), ("तुम्हारा नाम क्या है", "NAME"), ("तुम कौन हो", "NAME"), ("नमस्ते", "GREETING"), ("हेलो", "GREETING"), ("राम राम भाई", "GREETING"), ("तुम क्या कर सकते हो", "HELP"),
        ("इसे कैसे इस्तेमाल करूं", "HELP"), ("मदद करो", "HELP"), ("धन्यवाद", "THANKS"), ("शुक्रिया भाई", "THANKS"), ("कैसे हो", "HOW_ARE_YOU"), ("क्या हाल है", "HOW_ARE_YOU"),
        ("मेरा डेटा सुरक्षित है क्या", "PRIVACY"), ("क्या ठेकेदार मेरा हिसाब देख सकता है", "PRIVACY"), ("आज क्या तारीख है", "DATE"), ("तुम्हें किसने बनाया", "MAKER"), ("बाय", "BYE"), ("अच्छा फिर मिलेंगे", "BYE")],
 "or": [("ତୁମ ନାମ କଣ", "NAME"), ("ଆପଣଙ୍କ ନାଁ କଣ", "NAME"), ("ତୁମେ କିଏ", "NAME"), ("ନମସ୍କାର", "GREETING"), ("ହେଲୋ", "GREETING"), ("ତୁମେ କଣ କରିପାରିବ", "HELP"), ("ମୋତେ ସାହାଯ୍ୟ କର", "HELP"),
        ("ଏହାକୁ କିପରି ବ୍ୟବହାର କରିବି", "HELP"), ("ଧନ୍ୟବାଦ", "THANKS"), ("କେମିତି ଅଛ", "HOW_ARE_YOU"), ("ମୋ ଡାଟା ସୁରକ୍ଷିତ କି", "PRIVACY"), ("ଆଜି କି ତାରିଖ", "DATE"), ("ତୁମକୁ କିଏ ତିଆରି କଲା", "MAKER"), ("ବାଏ", "BYE")],
 "en": [("what is your name", "NAME"), ("who are you", "NAME"), ("tell me your name", "NAME"), ("hello", "GREETING"), ("hi", "GREETING"), ("good morning", "GREETING"), ("what can you do", "HELP"),
        ("how do I use this", "HELP"), ("help me", "HELP"), ("thank you", "THANKS"), ("thanks a lot", "THANKS"), ("how are you", "HOW_ARE_YOU"), ("is my data safe", "PRIVACY"),
        ("who can see my records", "PRIVACY"), ("what is the date today", "DATE"), ("who made you", "MAKER"), ("bye", "BYE"), ("goodbye", "BYE")],
 "te": [("నీ పేరు ఏమిటి", "NAME"), ("మీ పేరేంటి", "NAME"), ("నువ్వు ఎవరు", "NAME"), ("నమస్కారం", "GREETING"), ("హలో", "GREETING"), ("నువ్వు ఏం చేయగలవు", "HELP"), ("నాకు సహాయం చెయ్యి", "HELP"),
        ("ఇది ఎలా వాడాలి", "HELP"), ("ధన్యవాదాలు", "THANKS"), ("థాంక్స్", "THANKS"), ("ఎలా ఉన్నావు", "HOW_ARE_YOU"), ("నా డేటా సురక్షితమేనా", "PRIVACY"), ("ఈ రోజు తేదీ ఎంత", "DATE"), ("నిన్ను ఎవరు తయారు చేశారు", "MAKER"), ("బై", "BYE")],
 "kn": [("ನಿನ್ನ ಹೆಸರು ಏನು", "NAME"), ("ನಿಮ್ಮ ಹೆಸರೇನು", "NAME"), ("ನೀನು ಯಾರು", "NAME"), ("ನಮಸ್ಕಾರ", "GREETING"), ("ಹಲೋ", "GREETING"), ("ನೀನು ಏನು ಮಾಡಬಲ್ಲೆ", "HELP"), ("ನನಗೆ ಸಹಾಯ ಮಾಡು", "HELP"),
        ("ಇದನ್ನು ಹೇಗೆ ಬಳಸೋದು", "HELP"), ("ಧನ್ಯವಾದ", "THANKS"), ("ಥ್ಯಾಂಕ್ಸ್", "THANKS"), ("ಹೇಗಿದ್ದೀಯ", "HOW_ARE_YOU"), ("ನನ್ನ ಡೇಟಾ ಸುರಕ್ಷಿತವಾ", "PRIVACY"), ("ಇವತ್ತು ದಿನಾಂಕ ಎಷ್ಟು", "DATE"), ("ನಿನ್ನನ್ನು ಯಾರು ಮಾಡಿದ್ದು", "MAKER"), ("ಬೈ", "BYE")],
 "ta": [("உன் பெயர் என்ன", "NAME"), ("உங்க பேர் என்ன", "NAME"), ("நீ யார்", "NAME"), ("வணக்கம்", "GREETING"), ("ஹலோ", "GREETING"), ("நீ என்ன செய்ய முடியும்", "HELP"), ("எனக்கு உதவி பண்ணு", "HELP"),
        ("இதை எப்படி பயன்படுத்துறது", "HELP"), ("நன்றி", "THANKS"), ("எப்படி இருக்கீங்க", "HOW_ARE_YOU"), ("என் டேட்டா பாதுகாப்பா இருக்கா", "PRIVACY"), ("இன்னைக்கு தேதி என்ன", "DATE"), ("உன்னை யார் உருவாக்கினாங்க", "MAKER"), ("பை", "BYE")],
 "hi-Latn": [("tumhara naam kya hai", "NAME"), ("aap kaun ho", "NAME"), ("namaste", "GREETING"), ("hello bhai", "GREETING"), ("madad karo", "HELP"), ("ye kaise chalta hai", "HELP"), ("dhanyavad", "THANKS"),
        ("shukriya", "THANKS"), ("kaise ho", "HOW_ARE_YOU"), ("mera data safe hai kya", "PRIVACY"), ("aaj ki tarikh kya hai", "DATE"), ("tumhe kisne banaya", "MAKER"), ("bye", "BYE")],
}

# Out of scope: the app must not invent a ledger entry or answer; it explains what it can do.
UNKNOWN = {
 "hi": ["भारत की राजधानी क्या है", "एक चुटकुला सुनाओ", "कल बारिश होगी क्या", "गाना सुनाओ", "मुझे नौकरी चाहिए"],
 "or": ["ଭାରତର ରାଜଧାନୀ କଣ", "ଗୋଟେ ଗୀତ ଗାଅ", "ମୋତେ ଚାକିରି ଦରକାର"],
 "en": ["what is the capital of India", "tell me a joke", "will it rain tomorrow", "sing a song", "I need a job"],
 "te": ["భారతదేశ రాజధాని ఏది", "ఒక జోక్ చెప్పు", "రేపు వర్షం పడుతుందా"],
 "kn": ["ಭಾರತದ ರಾಜಧಾನಿ ಯಾವುದು", "ಒಂದು ಜೋಕ್ ಹೇಳು", "ನಾಳೆ ಮಳೆ ಬರುತ್ತಾ"],
 "ta": ["இந்தியாவின் தலைநகரம் எது", "ஒரு ஜோக் சொல்லு", "நாளைக்கு மழை வருமா"],
 "hi-Latn": ["bharat ki rajdhani kya hai", "ek chutkula sunao", "gaana sunao"],
}

H = [6, 7, 8, 9, 10, 12]; P = [80, 120, 150, 200, 250]; R = [3, 4, 5, 6, 8, 10]; A = [100, 200, 300, 500, 1000]
W = [350, 400, 500, 600, 650, 700, 800, 1500]; E = [20, 30, 40, 60, 80, 150, 250]; HR = [50, 60, 70, 80]

def entries(kind, v):
    e = lambda **k: k
    if kind == "piece": return [e(type="WORK", amount=v["P"] * v["R"], hours=v["H"], pieces=v["P"], rate=v["R"]), e(type="ADVANCE", amount=v["A"])]
    if kind == "wage_recv": return [e(type="WORK", amount=v["W"]), e(type="PAYMENT", amount=v["W"])]
    if kind == "wage_unpaid": return [e(type="WORK", amount=v["W"])]
    if kind == "adv": return [e(type="ADVANCE", amount=v["A"])]
    if kind == "yday": return [e(type="WORK", amount=v["W"], hours=v["H"], dayOffset=-1)]
    if kind == "hourly": return [e(type="WORK", amount=v["H"] * v["HR"], hours=v["H"], rate=v["HR"])]
    cat = {"exp_food": "FOOD", "exp_travel": "TRAVEL", "exp_health": "HEALTH"}[kind]
    return [e(type="EXPENSE", amount=v["E"], category=cat)]

if __name__ == "__main__":
    out = []
    for lang, t in T.items():
        for kind, template in t.items():
            if kind == "q":
                for text, period, topic in template:
                    out.append(dict(lang=lang, kind="query", text=text, period=period, topic=topic))
                continue
            for _ in range(5):
                v = dict(H=random.choice(H), P=random.choice(P), R=random.choice(R), A=random.choice(A), W=random.choice(W), E=random.choice(E), HR=random.choice(HR))
                forms = ["digits"] if lang == "hi-Latn" else ["words", "digits"]
                for form in forms:
                    fill = {k: (str(n) if form == "digits" else word(lang, n)) for k, n in v.items()}
                    out.append(dict(lang=lang, kind="entries", form=form, template=kind, text=template.format(**fill), entries=entries(kind, v)))
    for lang, items in CHAT.items():
        for text, chat in items: out.append(dict(lang=lang, kind="chat", text=text, chat=chat))
    for lang, items in UNKNOWN.items():
        for text in items: out.append(dict(lang=lang, kind="unknown", text=text))

    seen, unique = set(), []
    for o in out:
        if o["text"] not in seen: seen.add(o["text"]); unique.append(o)
    with open("core/src/test/resources/corpus.jsonl", "w", encoding="utf-8") as f:
        for o in unique: f.write(json.dumps(o, ensure_ascii=False) + "\n")
    from collections import Counter
    print(len(unique), "prompts", dict(Counter(o["lang"] for o in unique)), dict(Counter(o["kind"] for o in unique)))
