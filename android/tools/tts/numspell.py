"""Spells out numbers as words in Hindi, Odia, Telugu, Kannada, Tamil and English, the way the
app's spoken sentences need them (amounts, hours, pieces). Mirrored in core/NumberSpeller.kt."""
import re

UNITS = {
 "hi": "शून्य एक दो तीन चार पाँच छह सात आठ नौ दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस बीस इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस चालीस इकतालीस बयालीस तैंतालीस चौवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास पचास इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ साठ इकसठ बासठ तिरसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उनासी अस्सी इक्यासी बयासी तिरासी चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी नब्बे इक्यानवे बानवे तिरानवे चौरानवे पचानवे छियानवे सत्तानवे अट्ठानवे निन्यानवे",
 "or": "ଶୂନ ଏକ ଦୁଇ ତିନି ଚାରି ପାଞ୍ଚ ଛଅ ସାତ ଆଠ ନଅ ଦଶ ଏଗାର ବାର ତେର ଚଉଦ ପନ୍ଦର ଷୋହଳ ସତର ଅଠର ଉଣେଇଶ କୋଡ଼ିଏ ଏକୋଇଶ ବାଇଶ ତେଇଶ ଚବିଶ ପଚିଶ ଛବିଶ ସତାଇଶ ଅଠାଇଶ ଅଣତିରିଶ ତିରିଶ ଏକତିରିଶ ବତିଶ ତେତିଶ ଚଉତିରିଶ ପଞ୍ଚତିରିଶ ଛତିଶ ସଇଁତିରିଶ ଅଠତିରିଶ ଅଣଚାଳିଶ ଚାଳିଶ ଏକଚାଳିଶ ବୟାଳିଶ ତେୟାଳିଶ ଚଉରାଳିଶ ପଞ୍ଚଚାଳିଶ ଛୟାଳିଶ ସତଚାଳିଶ ଅଠଚାଳିଶ ଅଣଚାଶ ପଚାଶ ଏକାବନ ବାଉନ ତେପନ ଚଉବନ ପଞ୍ଚାବନ ଛପନ ସତାବନ ଅଠାବନ ଅଣଷଠି ଷାଠିଏ ଏକଷଠି ବାଷଠି ତେଷଠି ଚଉଷଠି ପଞ୍ଚଷଠି ଛଅଷଠି ସତଷଠି ଅଠଷଠି ଅଣସ୍ତରି ସତୁରି ଏକସ୍ତରି ବାସ୍ତରି ତେସ୍ତରି ଚଉସ୍ତରି ପଞ୍ଚସ୍ତରି ଛଅସ୍ତରି ସତସ୍ତରି ଅଠସ୍ତରି ଅଣାଅଶୀ ଅଶୀ ଏକାଅଶୀ ବୟାଅଶୀ ତେୟାଅଶୀ ଚଉରାଅଶୀ ପଞ୍ଚାଅଶୀ ଛୟାଅଶୀ ସତାଅଶୀ ଅଠାଅଶୀ ଅଣାନବେ ନବେ ଏକାନବେ ବୟାନବେ ତେୟାନବେ ଚଉରାନବେ ପଞ୍ଚାନବେ ଛୟାନବେ ସତାନବେ ଅଠାନବେ ଅନେଶତ",
 "te": "సున్నా ఒకటి రెండు మూడు నాలుగు ఐదు ఆరు ఏడు ఎనిమిది తొమ్మిది పది పదకొండు పన్నెండు పదమూడు పద్నాలుగు పదిహేను పదహారు పదిహేడు పద్దెనిమిది పందొమ్మిది ఇరవై ఇరవై_ఒకటి ఇరవై_రెండు ఇరవై_మూడు ఇరవై_నాలుగు ఇరవై_ఐదు ఇరవై_ఆరు ఇరవై_ఏడు ఇరవై_ఎనిమిది ఇరవై_తొమ్మిది ముప్పై",
 "kn": "ಸೊನ್ನೆ ಒಂದು ಎರಡು ಮೂರು ನಾಲ್ಕು ಐದು ಆರು ಏಳು ಎಂಟು ಒಂಬತ್ತು ಹತ್ತು ಹನ್ನೊಂದು ಹನ್ನೆರಡು ಹದಿಮೂರು ಹದಿನಾಲ್ಕು ಹದಿನೈದು ಹದಿನಾರು ಹದಿನೇಳು ಹದಿನೆಂಟು ಹತ್ತೊಂಬತ್ತು ಇಪ್ಪತ್ತು",
 "ta": "பூஜ்ஜியம் ஒன்று இரண்டு மூன்று நான்கு ஐந்து ஆறு ஏழு எட்டு ஒன்பது பத்து பதினொன்று பன்னிரண்டு பதிமூன்று பதினான்கு பதினைந்து பதினாறு பதினேழு பதினெட்டு பத்தொன்பது இருபது",
}
TENS = {  # for languages whose 21-99 are built as tens + unit
 "te": "ఇరవై ముప్పై నలభై యాభై అరవై డెబ్బై ఎనభై తొంభై", "kn": "ಇಪ್ಪತ್ತು ಮೂವತ್ತು ನಲವತ್ತು ಐವತ್ತು ಅರವತ್ತು ಎಪ್ಪತ್ತು ಎಂಬತ್ತು ತೊಂಬತ್ತು",
 "ta": "இருபது முப்பது நாற்பது ஐம்பது அறுபது எழுபது எண்பது தொண்ணூறு",
}
TENS_COMB = {"kn": "ಇಪ್ಪತ್ತ ಮೂವತ್ತ ನಲವತ್ತ ಐವತ್ತ ಅರವತ್ತ ಎಪ್ಪತ್ತ ಎಂಬತ್ತ ತೊಂಬತ್ತ", "ta": "இருபத்தி முப்பத்தி நாற்பத்தி ஐம்பத்தி அறுபத்தி எழுபத்தி எண்பத்தி தொண்ணூற்றி"}
HUNDREDS = {  # (exact hundreds, followed-by-more forms)
 "te": ("వంద రెండు_వందలు మూడు_వందలు నాలుగు_వందలు ఐదు_వందలు ఆరు_వందలు ఏడు_వందలు ఎనిమిది_వందలు తొమ్మిది_వందలు",
        "నూట రెండు_వందల మూడు_వందల నాలుగు_వందల ఐదు_వందల ఆరు_వందల ఏడు_వందల ఎనిమిది_వందల తొమ్మిది_వందల"),
 "kn": ("ನೂರು ಇನ್ನೂರು ಮುನ್ನೂರು ನಾನ್ನೂರು ಐನೂರು ಆರುನೂರು ಏಳುನೂರು ಎಂಟುನೂರು ಒಂಬೈನೂರು",
        "ನೂರ ಇನ್ನೂರ ಮುನ್ನೂರ ನಾನ್ನೂರ ಐನೂರ ಆರುನೂರ ಏಳುನೂರ ಎಂಟುನೂರ ಒಂಬೈನೂರ"),
 "ta": ("நூறு இருநூறு முந்நூறு நானூறு ஐநூறு அறுநூறு எழுநூறு எண்ணூறு தொள்ளாயிரம்",
        "நூற்று இருநூற்று முந்நூற்று நானூற்று ஐநூற்று அறுநூற்று எழுநூற்று எண்ணூற்று தொள்ளாயிரத்து"),
}
WORDS = {  # hundred, thousand, lakh (exact / followed-by-more) for hi, or, en
 "hi": ("सौ", "हज़ार", "लाख"), "or": ("ଶହ", "ହଜାର", "ଲକ୍ଷ"), "en": ("hundred", "thousand", "lakh"),
}
THOUSAND = {"te": ("వెయ్యి", "వేలు", "వేల"), "kn": ("ಸಾವಿರ", "ಸಾವಿರ", "ಸಾವಿರದ"), "ta": ("ஆயிரம்", "ஆயிரம்", "ஆயிரத்து")}
LAKH = {"te": "లక్షలు", "kn": "ಲಕ್ಷ", "ta": "லட்சம்"}
EN = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen".split()
EN_T = "twenty thirty forty fifty sixty seventy eighty ninety".split()
U = {k: v.split() for k, v in UNITS.items()}

def below100(n, lang, combining=False):
    if lang == "en":
        return EN[n] if n < 20 else EN_T[n // 10 - 2] + ("" if n % 10 == 0 else " " + EN[n % 10])
    if lang in ("hi", "or"): return U[lang][n]
    if n <= 20 or (lang == "te" and n < len(U["te"])): return U[lang][n].replace("_", " ")
    t, u = divmod(n, 10)
    if u == 0: return TENS[lang].split()[t - 2]
    head = (TENS_COMB.get(lang) or TENS[lang]).split()[t - 2]
    return f"{head} {U[lang][u]}"

def spell(n, lang, more=False):
    n = int(n); more = more and lang == "te"  # only Telugu marks a number before a noun
    if n < 100: return below100(n, lang)
    parts = []
    lakh, rest = divmod(n, 100000)
    if lakh:
        parts.append(f"{spell(lakh, lang)} {WORDS[lang][2] if lang in WORDS else LAKH[lang]}")
    th, rest = divmod(rest, 1000)
    if th:
        if lang in WORDS: parts.append(f"{spell(th, lang)} {WORDS[lang][1]}")
        else:
            one, many, comb = THOUSAND[lang]
            if th == 1: parts.append(one if rest == 0 and not more else ({"te": "వెయ్యి", "kn": "ಸಾವಿರದ", "ta": "ஆயிரத்து"}[lang]))
            else: parts.append(f"{spell(th, lang)} {many if rest == 0 and not more else comb}")
    h, rest = divmod(rest, 100)
    if h:
        if lang in WORDS: parts.append(f"{below100(h, lang)} {WORDS[lang][0]}")
        else:
            exact, comb = HUNDREDS[lang]
            parts.append((exact if rest == 0 and not more else comb).split()[h - 1].replace("_", " "))
    if rest: parts.append(below100(rest, lang))
    return " ".join(parts)

def spell_numbers(text, lang):
    def rep(m):
        s = m.group(0).replace(",", "")
        if "." in s:
            a, b = s.split(".", 1)
            return spell(a, lang) + (" " + {"hi": "दशमलव", "or": "ଦଶମିକ", "te": "పాయింట్", "kn": "ಪಾಯಿಂಟ್", "ta": "புள்ளி", "en": "point"}[lang] + " " + " ".join(spell(d, lang) for d in b) if b.strip("0") else "")
        more = text[m.end():].lstrip()[:1].isalpha()
        return spell(s, lang, more)
    return re.sub(r"\d[\d,]*(?:\.\d+)?", rep, text)
