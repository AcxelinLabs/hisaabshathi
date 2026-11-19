#!/usr/bin/env python3
"""200 spoken-style questions per app language (Hindi, Odia, Telugu, Kannada, Tamil, English)
for recording in real voices, to find where the app misses.

Each question is built from a hand-written phrasing (how people actually ask, with local and
mixed-in English words) and a way of saying the period ("this week", "is week", "hafte bhar
mein"...). It records what the app should answer:

- ledger questions: period (DAY, WEEK, MONTH, YEAR; ANY when no time is said) and topic
  (EARNED, ADVANCE, EXPENSE, DUE, ALL, MIN_WAGE);
- periods the app does not answer yet (YESTERDAY, LAST_WEEK, LAST_MONTH), marked
  supported = no, to measure how often workers would ask them;
- simple questions the app answers itself (TALK: DATE, HELP, NAME, PRIVACY, HOW_ARE_YOU, MAKER);
- everyday questions that are not about the ledger (NONE).

All text is best-effort and should be checked by native speakers while recording.

usage (from android/): make_voice_questions.py [xlsx_path]
  writes core/src/test/resources/voice_questions.jsonl (scored by VoiceQuestionsTest)
  and, when given, a workbook to record from.
"""
import json, random, sys

# period key -> (expected period, supported)
PERIOD = {"today": ("DAY", True), "week": ("WEEK", True), "month": ("MONTH", True), "year": ("YEAR", True),
          "yesterday": ("YESTERDAY", False), "last_week": ("LAST_WEEK", False), "last_month": ("LAST_MONTH", False)}

# Per language:
#   periods: key -> list of (adverb form {p}, modifier form {s}, English)
#   ledger: topic -> list of (phrasing, English); {p}/{s} = period, {e} = English period.
#           A phrasing without {p}/{s} is asked with no time said.
#   fixed: list of (text, English, expected) for questions with no period slot.
#   prefixes / suffixes: optional spoken additions, with English.
L = {}

L["hi"] = dict(
    name="Hindi",
    periods={
        "today": [("आज", "आज", "today"), ("आज दिन भर में", "आज दिन भर", "all day today")],
        "week": [("इस हफ्ते", "इस हफ्ते", "this week"), ("इस वीक", "इस वीक", "this week"), ("हफ्ते भर में", "हफ्ते भर", "over the week")],
        "month": [("इस महीने", "इस महीने", "this month"), ("इस मंथ", "इस मंथ", "this month"), ("महीने भर में", "महीने भर", "over the month")],
        "year": [("इस साल", "इस साल", "this year"), ("इस बरस", "इस बरस", "this year"), ("साल भर में", "साल भर", "over the year")],
        "yesterday": [("कल", "कल", "yesterday")],
        "last_week": [("पिछले हफ्ते", "पिछले हफ्ते", "last week")],
        "last_month": [("पिछले महीने", "पिछले महीने", "last month")],
    },
    ledger={
        "EARNED": [("{p} मैंने कितना कमाया", "how much did I earn {e}"), ("{p} कितनी कमाई हुई", "how much was earned {e}"),
                   ("{s} की कमाई कितनी है", "what are the earnings {e}"), ("{p} कितने पैसे बने", "how much money was made {e}"),
                   ("{p} काम करके कितना मिला", "how much did I get for my work {e}"), ("{p} मेरी दिहाड़ी कितनी बनी", "how much daily wage did I make {e}"),
                   ("{p} कितनी मजूरी बनी", "how much wage was made {e}"), ("{s} की इनकम कितनी है", "what is the income {e}"),
                   ("{p} कितने का काम किया", "how much work (in money) did I do {e}"), ("{s} की टोटल कमाई बताओ", "tell me the total earnings {e}"),
                   ("{p} कितना कमा लिया", "how much have I earned {e}")],
        "ADVANCE": [("{p} कितना एडवांस लिया", "how much advance did I take {e}"), ("{p} सेठ से कितना उठाया", "how much did I draw from the seth {e}"),
                    ("{p} ठेकेदार से कितना पैसा लिया", "how much money did I take from the contractor {e}"), ("{p} कितना उधार लिया", "how much did I borrow {e}"),
                    ("{p} पेशगी कितनी ली", "how much peshgi (advance) did I take {e}"), ("{p} मुंशी से कितना एडवांस लिया", "how much advance did I take from the munshi {e}"),
                    ("{s} का एडवांस कितना हुआ", "how much is the advance {e}"), ("{p} मालिक से कितने पैसे पहले ले लिए", "how much did I take early from the owner {e}"),
                    ("{p} कितना एडवांस उठाया", "how much advance did I draw {e}")],
        "EXPENSE": [("{p} कितना खर्चा हुआ", "how much was spent {e}"), ("{p} मैंने कितना खर्च किया", "how much did I spend {e}"),
                    ("{p} पैसे कहाँ गए", "where did the money go {e}"), ("{s} का खर्चा बताओ", "tell me the spending {e}"),
                    ("{p} कितने पैसे उड़ गए", "how much money flew away {e}"), ("{p} खाने पीने में कितना गया", "how much went on food {e}"),
                    ("{p} किराए भाड़े में कितना लगा", "how much went on fares {e}"), ("{p} घर कितना भेजा", "how much did I send home {e}"),
                    ("{s} का टोटल खर्चा कितना है", "what is the total spending {e}"), ("{p} कितना पैसा लग गया", "how much money got used up {e}")],
        "DUE": [("सेठ पर अभी कितना बाकी है", "how much does the seth still owe"), ("{p} कितना पैसा बाकी है", "how much money is pending {e}"),
                ("ठेकेदार को अभी मुझे कितना देना है", "how much does the contractor still have to give me"),
                ("{s} का पूरा पैसा मिला क्या", "did I get all the money {e}"), ("मेरा कितना पैसा फंसा हुआ है", "how much of my money is stuck"),
                ("{p} कितना लेना बाकी है", "how much is left to collect {e}"), ("मालिक ने अभी कितना नहीं दिया", "how much has the owner not paid yet"),
                ("{s} का बकाया कितना है", "how much are the dues {e}"), ("{p} कितना पेमेंट रुका है", "how much payment is held up {e}")],
        "ALL": [("{s} का पूरा हिसाब बताओ", "tell me the full account {e}"), ("{s} का हिसाब किताब सुनाओ", "read out the accounts {e}"),
                ("{s} का लेखा जोखा बताओ", "tell me the ledger {e}"), ("{s} का सब कुछ बताओ", "tell me everything {e}"),
                ("{s} की रिपोर्ट दिखाओ", "show the report {e}"), ("{s} का पूरा ब्यौरा दो", "give the full details {e}"),
                ("{p} हिसाब में क्या क्या हुआ", "what all happened in the accounts {e}"), ("{s} की समरी बताओ", "tell me the summary {e}")],
    },
    fixed=[("सरकारी मजदूरी कितनी है", "what is the government wage", "ASK ANY MIN_WAGE"),
           ("न्यूनतम मजदूरी कितनी है", "what is the minimum wage", "ASK ANY MIN_WAGE"),
           ("सरकार ने दिहाड़ी कितनी तय की है", "what daily wage has the government fixed", "ASK ANY MIN_WAGE"),
           ("मिनिमम वेज कितना है", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("एक दिन का सरकारी रेट क्या है", "what is the government rate for a day", "ASK ANY MIN_WAGE"),
           ("मजदूरी का सरकारी रेट बताओ", "tell me the government wage rate", "ASK ANY MIN_WAGE"),
           ("आज तारीख क्या है", "what is the date today", "TALK DATE"), ("आज कौन सा दिन है", "what day is it today", "TALK DATE"),
           ("तुम क्या क्या कर सकते हो", "what all can you do", "TALK HELP"), ("ये ऐप कैसे चलाऊं", "how do I use this app", "TALK HELP"),
           ("तुम्हारा नाम क्या है", "what is your name", "TALK NAME"), ("तुम कौन हो", "who are you", "TALK NAME"),
           ("मेरा हिसाब कोई और देख सकता है क्या", "can anyone else see my accounts", "TALK PRIVACY"),
           ("कैसे हो", "how are you", "TALK HOW_ARE_YOU"), ("तुम्हें किसने बनाया", "who made you", "TALK MAKER"),
           ("आज बारिश होगी क्या", "will it rain today", "NONE"), ("बस कब आएगी", "when will the bus come", "NONE"),
           ("मेरा मोबाइल रिचार्ज कर दो", "recharge my mobile", "NONE"), ("पास में अस्पताल कहाँ है", "where is the hospital nearby", "NONE"),
           ("राशन कब मिलेगा", "when will we get ration", "NONE"), ("गाना बजाओ", "play a song", "NONE"),
           ("ट्रेन कितने बजे है", "what time is the train", "NONE"), ("क्रिकेट का स्कोर क्या है", "what is the cricket score", "NONE")],
    prefixes=[("", ""), ("भाई ", "brother, "), ("अरे ", "hey, "), ("सुनो ", "listen, ")],
    suffixes=[("", ""), (" बताओ", ", tell me"), (" बता दो ना", ", please tell me")],
)

L["or"] = dict(
    name="Odia",
    periods={
        "today": [("ଆଜି", "ଆଜିର", "today")],
        "week": [("ଏ ସପ୍ତାହରେ", "ଏ ସପ୍ତାହର", "this week"), ("ଏ ହପ୍ତାରେ", "ଏ ହପ୍ତାର", "this week (hapta)"), ("ଏ ୱିକରେ", "ଏ ୱିକର", "this week")],
        "month": [("ଏ ମାସରେ", "ଏ ମାସର", "this month"), ("ଚଳିତ ମାସରେ", "ଚଳିତ ମାସର", "this current month")],
        "year": [("ଏ ବର୍ଷ", "ଏ ବର୍ଷର", "this year"), ("ଚଳିତ ବର୍ଷରେ", "ଚଳିତ ବର୍ଷର", "this current year")],
        "yesterday": [("କାଲି", "କାଲିର", "yesterday")],
        "last_week": [("ଗଲା ସପ୍ତାହରେ", "ଗଲା ସପ୍ତାହର", "last week")],
        "last_month": [("ଗତ ମାସରେ", "ଗତ ମାସର", "last month")],
    },
    ledger={
        "EARNED": [("{p} ମୁଁ କେତେ ରୋଜଗାର କଲି", "how much did I earn {e}"), ("{p} କେତେ ଟଙ୍କା କମେଇଲି", "how many rupees did I make {e}"),
                   ("{s} ରୋଜଗାର କେତେ", "what are the earnings {e}"), ("{p} ମୋ ମଜୁରି କେତେ ହେଲା", "how much did my wages come to {e}"),
                   ("{p} କାମ କରି କେତେ ପାଇଲି", "how much did I get for working {e}"), ("{s} ଇନକମ୍ କେତେ", "what is the income {e}"),
                   ("{p} ମୋ ହାଜିରା କେତେ ହେଲା", "how much did my daily pay (hajira) come to {e}"), ("{p} କାମରେ କେତେ ଟଙ୍କା ହେଲା", "how much money did the work make {e}"),
                   ("{p} କେତେ କମେଇଲି କହିଲ", "say how much I earned {e}")],
        "ADVANCE": [("{p} କେତେ ଆଡଭାନ୍ସ ନେଲି", "how much advance did I take {e}"), ("{p} ଠିକାଦାରଙ୍କଠୁ କେତେ ଟଙ୍କା ଆଗୁଆ ନେଲି", "how much did I take ahead from the contractor {e}"),
                    ("{p} ମାଲିକଙ୍କଠୁ କେତେ ଧାର ନେଲି", "how much did I borrow from the owner {e}"), ("{p} ସର୍ଦ୍ଦାରଙ୍କଠୁ କେତେ ନେଇଛି", "how much have I taken from the sardar {e}"),
                    ("{s} ଆଡଭାନ୍ସ କେତେ", "how much is the advance {e}"), ("{p} ଦାଦନ କେତେ ନେଲି", "how much dadan (advance) did I take {e}")],
        "EXPENSE": [("{p} କେତେ ଖର୍ଚ୍ଚ ହେଲା", "how much was spent {e}"), ("{p} ମୁଁ କେତେ ଖର୍ଚ୍ଚ କଲି", "how much did I spend {e}"),
                    ("{p} ଟଙ୍କା କୁଆଡେ ଗଲା", "where did the money go {e}"), ("{s} ଖର୍ଚ୍ଚ କହ", "tell me the spending {e}"),
                    ("{p} ଖାଇବାରେ କେତେ ଗଲା", "how much went on food {e}"), ("{p} ଘରକୁ କେତେ ପଠାଇଲି", "how much did I send home {e}"),
                    ("{p} ଗାଡି ଭଡାରେ କେତେ ଗଲା", "how much went on vehicle fare {e}")],
        "DUE": [("ମାଲିକ ପାଖରେ ଆଉ କେତେ ବାକି ଅଛି", "how much more is pending with the owner"), ("{p} କେତେ ଟଙ୍କା ବାକି ରହିଲା", "how much money is left pending {e}"),
                ("ଠିକାଦାର ମୋତେ ଆଉ କେତେ ଦେବେ", "how much more will the contractor give me"), ("{s} ପୁରା ମଜୁରି ମିଳିଲା କି", "did I get the full wages {e}"),
                ("ମୋର କେତେ ଟଙ୍କା ଅଟକିଛି", "how much of my money is stuck"), ("{s} ବାକି ପଇସା କେତେ", "how much money is pending {e}"),
                ("ସର୍ଦ୍ଦାର ମୋତେ କେତେ ଦେବା କଥା", "how much is the sardar supposed to give me")],
        "ALL": [("{s} ପୁରା ହିସାବ କୁହ", "tell me the full account {e}"), ("{s} ହିସାବ ଦେଖାଅ", "show the account {e}"),
                ("{s} ସବୁ ହିସାବ ଶୁଣାଅ", "read out all the accounts {e}"), ("{s} ହିସାବ କିତାବ କହିଲ", "tell me the accounts {e}"),
                ("{s} ରିପୋର୍ଟ ଦେଖାଅ", "show the report {e}"), ("{s} ପୁରା ଲେଖାଜୋଖା", "the full ledger {e}")],
    },
    fixed=[("ସରକାରୀ ମଜୁରି କେତେ", "how much is the government wage", "ASK ANY MIN_WAGE"),
           ("ସର୍ବନିମ୍ନ ମଜୁରି କେତେ", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("ସରକାର ଦିନକୁ କେତେ ମଜୁରି ଠିକ୍ କରିଛନ୍ତି", "what daily wage has the government fixed", "ASK ANY MIN_WAGE"),
           ("ମିନିମମ୍ ୱେଜ୍ କେତେ", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("ଦିନକୁ ସରକାରୀ ହାଜିରା କେତେ", "what is the government daily pay", "ASK ANY MIN_WAGE"),
           ("ଆଜି କେତେ ତାରିଖ", "what is the date today", "TALK DATE"), ("ଆଜି କି ବାର", "what day of the week is it today", "TALK DATE"),
           ("ତୁମେ କଣ କଣ କରିପାରିବ", "what all can you do", "TALK HELP"), ("ଏହାକୁ କେମିତି ଚଳାଇବି", "how do I use this", "TALK HELP"),
           ("ତୁମ ନାଁ କଣ", "what is your name", "TALK NAME"), ("ତୁମେ କିଏ", "who are you", "TALK NAME"),
           ("ମୋ ହିସାବ ଆଉ କେହି ଦେଖିପାରିବେ କି", "can anyone else see my accounts", "TALK PRIVACY"),
           ("କେମିତି ଅଛ", "how are you", "TALK HOW_ARE_YOU"), ("ତୁମକୁ କିଏ ତିଆରି କଲା", "who made you", "TALK MAKER"),
           ("ଆଜି ବର୍ଷା ହେବ କି", "will it rain today", "NONE"), ("ବସ୍ କେତେବେଳେ ଆସିବ", "when will the bus come", "NONE"),
           ("ପାଖରେ ଡାକ୍ତରଖାନା କେଉଁଠି", "where is the hospital nearby", "NONE"), ("ରାସନ୍ କେବେ ମିଳିବ", "when will we get ration", "NONE"),
           ("ଗୀତ ଶୁଣାଅ", "play a song", "NONE"), ("ଟ୍ରେନ୍ କେତେବେଳେ ଛାଡିବ", "when does the train leave", "NONE"),
           ("ମୋ ମୋବାଇଲ୍ ରିଚାର୍ଜ କରିଦିଅ", "recharge my mobile", "NONE")],
    prefixes=[("", ""), ("ଭାଇ ", "brother, "), ("ଆରେ ", "hey, "), ("ଶୁଣ ", "listen, ")],
    suffixes=[("", ""), (" କୁହ", ", tell me"), (" ଟିକେ କହିଲ", ", please tell me")],
)

L["te"] = dict(
    name="Telugu",
    periods={
        "today": [("ఈరోజు", "ఈరోజు", "today"), ("ఇవాళ", "ఇవాళ్టి", "today (ivala)")],
        "week": [("ఈ వారం", "ఈ వారం", "this week"), ("ఈ వీక్", "ఈ వీక్", "this week"), ("ఈ వారంలో", "ఈ వారం", "within this week")],
        "month": [("ఈ నెల", "ఈ నెల", "this month"), ("ఈ నెలలో", "ఈ నెల", "within this month"), ("ఈ మంత్", "ఈ మంత్", "this month")],
        "year": [("ఈ సంవత్సరం", "ఈ సంవత్సరం", "this year"), ("ఈ ఏడాది", "ఈ ఏడాది", "this year (yedadi)")],
        "yesterday": [("నిన్న", "నిన్నటి", "yesterday")],
        "last_week": [("పోయిన వారం", "పోయిన వారం", "last week")],
        "last_month": [("పోయిన నెల", "పోయిన నెల", "last month")],
    },
    ledger={
        "EARNED": [("{p} నేను ఎంత సంపాదించాను", "how much did I earn {e}"), ("{p} ఎంత కూలి వచ్చింది", "how much wage came {e}"),
                   ("{p} పనికి ఎంత డబ్బులు వచ్చాయి", "how much money came for the work {e}"), ("{s} సంపాదన ఎంత", "what are the earnings {e}"),
                   ("{s} కూలి డబ్బులు ఎంత", "how much is the wage money {e}"), ("{p} ఎంత సంపాదించానో చెప్పు", "tell me how much I earned {e}"),
                   ("{p} నా జీతం ఎంత అయింది", "how much did my pay come to {e}"), ("{s} ఇన్కమ్ ఎంత", "what is the income {e}"),
                   ("{p} ఎన్ని రూపాయలు సంపాదించా", "how many rupees did I earn {e}")],
        "ADVANCE": [("{p} ఎంత అడ్వాన్స్ తీసుకున్నాను", "how much advance did I take {e}"), ("{p} మేస్త్రి దగ్గర ఎంత తీసుకున్నా", "how much did I take from the mestri {e}"),
                    ("{p} ఓనర్ దగ్గర ఎంత అప్పు తీసుకున్నాను", "how much did I borrow from the owner {e}"), ("{p} ముందుగా ఎంత డబ్బు తీసుకున్నాను", "how much money did I take beforehand {e}"),
                    ("{s} అడ్వాన్స్ ఎంత", "how much is the advance {e}"), ("{p} కాంట్రాక్టర్ దగ్గర ఎంత అడ్వాన్స్ తీసుకున్నా", "how much advance did I take from the contractor {e}")],
        "EXPENSE": [("{p} ఎంత ఖర్చు అయింది", "how much was spent {e}"), ("{p} నేను ఎంత ఖర్చు పెట్టాను", "how much did I spend {e}"),
                    ("{p} డబ్బులు ఎక్కడికి పోయాయి", "where did the money go {e}"), ("{s} ఖర్చులు చెప్పు", "tell me the expenses {e}"),
                    ("{p} తిండికి ఎంత అయింది", "how much went on food {e}"), ("{p} ఇంటికి ఎంత పంపించాను", "how much did I send home {e}"),
                    ("{p} బస్సు చార్జీలకి ఎంత పోయింది", "how much went on bus fares {e}")],
        "DUE": [("ఓనర్ నాకు ఇంకా ఎంత ఇవ్వాలి", "how much more does the owner have to give me"), ("{p} ఇంకా ఎంత బాకీ ఉంది", "how much is still pending {e}"),
                ("మేస్త్రి దగ్గర నుంచి ఇంకా ఎంత రావాలి", "how much more is to come from the mestri"), ("{s} కూలి మొత్తం వచ్చిందా", "did the full wage come {e}"),
                ("నా డబ్బులు ఎంత ఆగిపోయాయి", "how much of my money is held up"), ("{s} బాకీ ఎంత", "how much is pending {e}"),
                ("ఇంకా ఎంత రావాల్సి ఉంది", "how much is still to come")],
        "ALL": [("{s} మొత్తం లెక్క చెప్పు", "tell me the whole account {e}"), ("{s} లెక్క చూపించు", "show the account {e}"),
                ("{s} లెక్కలు అన్నీ చెప్పు", "tell me all the accounts {e}"), ("{s} పూర్తి వివరాలు చెప్పు", "tell me the full details {e}"),
                ("{s} రిపోర్ట్ చూపించు", "show the report {e}"), ("{s} హిసాబ్ చెప్పు", "tell me the hisab {e}")],
    },
    fixed=[("ప్రభుత్వ కూలి రేటు ఎంత", "what is the government wage rate", "ASK ANY MIN_WAGE"),
           ("కనీస వేతనం ఎంత", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("గవర్నమెంట్ రోజుకి ఎంత కూలి ఇవ్వమంది", "how much daily wage did the government say to pay", "ASK ANY MIN_WAGE"),
           ("మినిమమ్ వేజ్ ఎంత", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("రోజు కూలి సర్కారు రేటు ఎంత", "what is the government rate for daily wage", "ASK ANY MIN_WAGE"),
           ("ఇవాళ తేదీ ఎంత", "what is the date today", "TALK DATE"), ("ఈరోజు ఏ వారం", "what day of the week is it today", "TALK DATE"),
           ("నువ్వు ఏమేమి చేయగలవు", "what all can you do", "TALK HELP"), ("ఇది ఎలా వాడాలి", "how do I use this", "TALK HELP"),
           ("నీ పేరు ఏంటి", "what is your name", "TALK NAME"), ("నువ్వు ఎవరు", "who are you", "TALK NAME"),
           ("నా లెక్కలు ఇంకెవరైనా చూడగలరా", "can anyone else see my accounts", "TALK PRIVACY"),
           ("ఎలా ఉన్నావు", "how are you", "TALK HOW_ARE_YOU"), ("నిన్ను ఎవరు తయారు చేశారు", "who made you", "TALK MAKER"),
           ("ఇవాళ వర్షం పడుతుందా", "will it rain today", "NONE"), ("బస్సు ఎప్పుడు వస్తుంది", "when will the bus come", "NONE"),
           ("దగ్గర్లో ఆసుపత్రి ఎక్కడ ఉంది", "where is the hospital nearby", "NONE"), ("రేషన్ ఎప్పుడు ఇస్తారు", "when will they give ration", "NONE"),
           ("పాట పెట్టు", "play a song", "NONE"), ("ట్రైన్ ఎన్ని గంటలకి", "what time is the train", "NONE"),
           ("నా ఫోన్ రీఛార్జ్ చేయి", "recharge my phone", "NONE")],
    prefixes=[("", ""), ("అన్నా ", "brother, "), ("ఏయ్ ", "hey, "), ("విను ", "listen, ")],
    suffixes=[("", ""), (" చెప్పు", ", tell me"), (" కొంచెం చెప్పు", ", please tell me")],
)

L["kn"] = dict(
    name="Kannada",
    periods={
        "today": [("ಇವತ್ತು", "ಇವತ್ತಿನ", "today"), ("ಇಂದು", "ಇಂದಿನ", "today (formal)")],
        "week": [("ಈ ವಾರ", "ಈ ವಾರದ", "this week"), ("ಈ ವೀಕ್", "ಈ ವೀಕ್", "this week"), ("ಈ ವಾರದಲ್ಲಿ", "ಈ ವಾರದ", "within this week")],
        "month": [("ಈ ತಿಂಗಳು", "ಈ ತಿಂಗಳ", "this month"), ("ಈ ತಿಂಗಳಲ್ಲಿ", "ಈ ತಿಂಗಳ", "within this month")],
        "year": [("ಈ ವರ್ಷ", "ಈ ವರ್ಷದ", "this year"), ("ಈ ವರ್ಷದಲ್ಲಿ", "ಈ ವರ್ಷದ", "within this year")],
        "yesterday": [("ನಿನ್ನೆ", "ನಿನ್ನೆಯ", "yesterday")],
        "last_week": [("ಹೋದ ವಾರ", "ಹೋದ ವಾರದ", "last week")],
        "last_month": [("ಹೋದ ತಿಂಗಳು", "ಹೋದ ತಿಂಗಳ", "last month")],
    },
    ledger={
        "EARNED": [("{p} ನಾನು ಎಷ್ಟು ದುಡಿದೆ", "how much did I earn {e}"), ("{p} ಎಷ್ಟು ಸಂಪಾದನೆ ಆಯ್ತು", "how much was earned {e}"),
                   ("{s} ಕೂಲಿ ಎಷ್ಟು", "how much is the wage {e}"), ("{p} ಕೆಲಸ ಮಾಡಿ ಎಷ್ಟು ಬಂತು", "how much came from working {e}"),
                   ("{s} ಇನ್ಕಮ್ ಎಷ್ಟು", "what is the income {e}"), ("{p} ಎಷ್ಟು ದುಡ್ಡು ಮಾಡಿದೆ ಹೇಳು", "tell me how much money I made {e}"),
                   ("{p} ನನ್ನ ಸಂಬಳ ಎಷ್ಟಾಯ್ತು", "how much did my pay come to {e}"), ("{s} ಒಟ್ಟು ಸಂಪಾದನೆ ಎಷ್ಟು", "what are the total earnings {e}")],
        "ADVANCE": [("{p} ಎಷ್ಟು ಅಡ್ವಾನ್ಸ್ ತಗೊಂಡೆ", "how much advance did I take {e}"), ("{p} ಮೇಸ್ತ್ರಿ ಹತ್ರ ಎಷ್ಟು ತಗೊಂಡಿದೀನಿ", "how much have I taken from the mestri {e}"),
                    ("{p} ಓನರ್ ಹತ್ರ ಎಷ್ಟು ಸಾಲ ತಗೊಂಡೆ", "how much did I borrow from the owner {e}"), ("{p} ಮುಂಗಡ ಎಷ್ಟು ತಗೊಂಡೆ", "how much mungada (advance) did I take {e}"),
                    ("{s} ಅಡ್ವಾನ್ಸ್ ಎಷ್ಟು", "how much is the advance {e}"), ("{p} ಕಂಟ್ರಾಕ್ಟರ್ ಹತ್ರ ಎಷ್ಟು ದುಡ್ಡು ಇಸ್ಕೊಂಡೆ", "how much money did I take from the contractor {e}")],
        "EXPENSE": [("{p} ಎಷ್ಟು ಖರ್ಚಾಯ್ತು", "how much was spent {e}"), ("{p} ನಾನು ಎಷ್ಟು ಖರ್ಚು ಮಾಡಿದೆ", "how much did I spend {e}"),
                    ("{p} ದುಡ್ಡು ಎಲ್ಲಿ ಹೋಯ್ತು", "where did the money go {e}"), ("{s} ಖರ್ಚು ಹೇಳು", "tell me the spending {e}"),
                    ("{p} ಊಟಕ್ಕೆ ಎಷ್ಟು ಹೋಯ್ತು", "how much went on meals {e}"), ("{p} ಮನೆಗೆ ಎಷ್ಟು ಕಳಿಸಿದೆ", "how much did I send home {e}"),
                    ("{p} ಬಸ್ ಚಾರ್ಜ್‌ಗೆ ಎಷ್ಟು ಆಯ್ತು", "how much went on bus fare {e}")],
        "DUE": [("ಓನರ್ ನನಗೆ ಇನ್ನೂ ಎಷ್ಟು ಕೊಡಬೇಕು", "how much more does the owner have to give me"), ("{p} ಇನ್ನೂ ಎಷ್ಟು ಬಾಕಿ ಇದೆ", "how much is still pending {e}"),
                ("ಮೇಸ್ತ್ರಿ ಇಂದ ಇನ್ನೂ ಎಷ್ಟು ಬರಬೇಕು", "how much more is to come from the mestri"), ("{s} ಪೂರ್ತಿ ಕೂಲಿ ಸಿಕ್ತಾ", "did I get the full wage {e}"),
                ("ನನ್ನ ಎಷ್ಟು ದುಡ್ಡು ನಿಂತಿದೆ", "how much of my money is held up"), ("{s} ಬಾಕಿ ಎಷ್ಟು", "how much is pending {e}"),
                ("ಇನ್ನೂ ಎಷ್ಟು ಬರೋದಿದೆ", "how much is still to come")],
        "ALL": [("{s} ಪೂರ್ತಿ ಲೆಕ್ಕ ಹೇಳು", "tell me the full account {e}"), ("{s} ಲೆಕ್ಕ ತೋರಿಸು", "show the account {e}"),
                ("{s} ಎಲ್ಲಾ ಲೆಕ್ಕ ಹೇಳು", "tell me all the accounts {e}"), ("{s} ಪೂರ್ತಿ ವಿವರ ಕೊಡು", "give the full details {e}"),
                ("{s} ರಿಪೋರ್ಟ್ ತೋರಿಸು", "show the report {e}"), ("{s} ಹಿಸಾಬ್ ಹೇಳು", "tell me the hisab {e}")],
    },
    fixed=[("ಸರ್ಕಾರದ ಕೂಲಿ ದರ ಎಷ್ಟು", "what is the government wage rate", "ASK ANY MIN_WAGE"),
           ("ಕನಿಷ್ಠ ವೇತನ ಎಷ್ಟು", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("ಸರ್ಕಾರ ದಿನಕ್ಕೆ ಎಷ್ಟು ಕೂಲಿ ಕೊಡಬೇಕು ಅಂತ ಹೇಳಿದೆ", "how much daily wage has the government said to pay", "ASK ANY MIN_WAGE"),
           ("ಮಿನಿಮಮ್ ವೇಜ್ ಎಷ್ಟು", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("ದಿನದ ಕೂಲಿ ಸರ್ಕಾರಿ ರೇಟ್ ಎಷ್ಟು", "what is the government rate for a day's wage", "ASK ANY MIN_WAGE"),
           ("ಇವತ್ತು ದಿನಾಂಕ ಎಷ್ಟು", "what is the date today", "TALK DATE"), ("ಇವತ್ತು ಯಾವ ವಾರ", "what day of the week is it today", "TALK DATE"),
           ("ನೀನು ಏನೇನು ಮಾಡ್ತೀಯ", "what all can you do", "TALK HELP"), ("ಇದನ್ನ ಹೇಗೆ ಉಪಯೋಗಿಸೋದು", "how do I use this", "TALK HELP"),
           ("ನಿನ್ನ ಹೆಸರು ಏನು", "what is your name", "TALK NAME"), ("ನೀನು ಯಾರು", "who are you", "TALK NAME"),
           ("ನನ್ನ ಲೆಕ್ಕ ಬೇರೆ ಯಾರಾದ್ರೂ ನೋಡಬಹುದಾ", "can anyone else see my accounts", "TALK PRIVACY"),
           ("ಹೇಗಿದ್ದೀಯ", "how are you", "TALK HOW_ARE_YOU"), ("ನಿನ್ನನ್ನ ಯಾರು ಮಾಡಿದ್ರು", "who made you", "TALK MAKER"),
           ("ಇವತ್ತು ಮಳೆ ಬರುತ್ತಾ", "will it rain today", "NONE"), ("ಬಸ್ ಯಾವಾಗ ಬರುತ್ತೆ", "when will the bus come", "NONE"),
           ("ಹತ್ತಿರ ಆಸ್ಪತ್ರೆ ಎಲ್ಲಿದೆ", "where is the hospital nearby", "NONE"), ("ರೇಷನ್ ಯಾವಾಗ ಕೊಡ್ತಾರೆ", "when will they give ration", "NONE"),
           ("ಹಾಡು ಹಾಕು", "play a song", "NONE"), ("ಟ್ರೈನ್ ಎಷ್ಟು ಗಂಟೆಗೆ", "what time is the train", "NONE"),
           ("ನನ್ನ ಫೋನ್ ರೀಚಾರ್ಜ್ ಮಾಡು", "recharge my phone", "NONE")],
    prefixes=[("", ""), ("ಅಣ್ಣಾ ", "brother, "), ("ಏ ", "hey, "), ("ಕೇಳು ", "listen, ")],
    suffixes=[("", ""), (" ಹೇಳು", ", tell me"), (" ಸ್ವಲ್ಪ ಹೇಳು", ", please tell me")],
)

L["ta"] = dict(
    name="Tamil",
    periods={
        "today": [("இன்னைக்கு", "இன்னைக்கு", "today"), ("இன்று", "இன்றைய", "today (formal)")],
        "week": [("இந்த வாரம்", "இந்த வார", "this week"), ("இந்த வீக்", "இந்த வீக்", "this week")],
        "month": [("இந்த மாசம்", "இந்த மாச", "this month"), ("இந்த மாதம்", "இந்த மாத", "this month (formal)")],
        "year": [("இந்த வருஷம்", "இந்த வருஷ", "this year"), ("இந்த வருடம்", "இந்த வருட", "this year (formal)")],
        "yesterday": [("நேத்து", "நேத்து", "yesterday")],
        "last_week": [("போன வாரம்", "போன வார", "last week")],
        "last_month": [("போன மாசம்", "போன மாச", "last month")],
    },
    ledger={
        "EARNED": [("{p} நான் எவ்வளவு சம்பாதிச்சேன்", "how much did I earn {e}"), ("{p} எவ்வளவு கூலி வந்துச்சு", "how much wage came {e}"),
                   ("{s} வருமானம் எவ்வளவு", "what is the income {e}"), ("{p} வேலை செஞ்சு எவ்வளவு கிடைச்சுது", "how much did I get for working {e}"),
                   ("{s} இன்கம் எவ்வளவு", "what is the income {e}"), ("{p} எவ்வளவு சம்பாதிச்சேன்னு சொல்லு", "tell me how much I earned {e}"),
                   ("{p} என் சம்பளம் எவ்வளவு ஆச்சு", "how much did my pay come to {e}"), ("{p} எத்தனை ரூபா வந்துச்சு", "how many rupees came {e}")],
        "ADVANCE": [("{p} எவ்வளவு அட்வான்ஸ் வாங்கினேன்", "how much advance did I take {e}"), ("{p} மேஸ்திரிகிட்ட எவ்வளவு வாங்கினேன்", "how much did I take from the mestri {e}"),
                    ("{p} முதலாளிகிட்ட எவ்வளவு கடன் வாங்கினேன்", "how much did I borrow from the owner {e}"), ("{p} முன்பணம் எவ்வளவு வாங்கினேன்", "how much munpanam (advance) did I take {e}"),
                    ("{s} அட்வான்ஸ் எவ்வளவு", "how much is the advance {e}"), ("{p} கான்ட்ராக்டர்கிட்ட எவ்வளவு பணம் வாங்கினேன்", "how much money did I take from the contractor {e}")],
        "EXPENSE": [("{p} எவ்வளவு செலவு ஆச்சு", "how much was spent {e}"), ("{p} நான் எவ்வளவு செலவு பண்ணேன்", "how much did I spend {e}"),
                    ("{p} காசு எங்க போச்சு", "where did the money go {e}"), ("{s} செலவு சொல்லு", "tell me the spending {e}"),
                    ("{p} சாப்பாட்டுக்கு எவ்வளவு போச்சு", "how much went on food {e}"), ("{p} வீட்டுக்கு எவ்வளவு அனுப்பினேன்", "how much did I send home {e}"),
                    ("{p} பஸ் சார்ஜுக்கு எவ்வளவு ஆச்சு", "how much went on bus fare {e}")],
        "DUE": [("முதலாளி இன்னும் எனக்கு எவ்வளவு தரணும்", "how much more does the owner have to give me"), ("{p} இன்னும் எவ்வளவு பாக்கி இருக்கு", "how much is still pending {e}"),
                ("மேஸ்திரிகிட்ட இருந்து இன்னும் எவ்வளவு வரணும்", "how much more is to come from the mestri"), ("{s} முழு கூலி கிடைச்சுதா", "did I get the full wage {e}"),
                ("என் காசு எவ்வளவு நிக்குது", "how much of my money is held up"), ("{s} பாக்கி எவ்வளவு", "how much is pending {e}"),
                ("இன்னும் எவ்வளவு வர வேண்டியிருக்கு", "how much is still to come")],
        "ALL": [("{s} முழு கணக்கு சொல்லு", "tell me the full account {e}"), ("{s} கணக்கு காட்டு", "show the account {e}"),
                ("{s} கணக்கு எல்லாம் சொல்லு", "tell me all the accounts {e}"), ("{s} முழு விவரம் சொல்லு", "tell me the full details {e}"),
                ("{s} ரிப்போர்ட் காட்டு", "show the report {e}"), ("{s} கணக்கு வழக்கு சொல்லு", "tell me the accounts {e}")],
    },
    fixed=[("அரசாங்க கூலி எவ்வளவு", "how much is the government wage", "ASK ANY MIN_WAGE"),
           ("குறைந்தபட்ச கூலி எவ்வளவு", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("அரசு ஒரு நாளுக்கு எவ்வளவு கூலி நிர்ணயிச்சிருக்கு", "what daily wage has the government fixed", "ASK ANY MIN_WAGE"),
           ("மினிமம் வேஜ் எவ்வளவு", "how much is the minimum wage", "ASK ANY MIN_WAGE"),
           ("ஒரு நாள் கூலிக்கு கவர்ன்மென்ட் ரேட் என்ன", "what is the government rate for a day's wage", "ASK ANY MIN_WAGE"),
           ("இன்னைக்கு தேதி என்ன", "what is the date today", "TALK DATE"), ("இன்னைக்கு என்ன கிழமை", "what day of the week is it today", "TALK DATE"),
           ("நீ என்னென்ன செய்வ", "what all can you do", "TALK HELP"), ("இதை எப்படி யூஸ் பண்றது", "how do I use this", "TALK HELP"),
           ("உன் பேர் என்ன", "what is your name", "TALK NAME"), ("நீ யாரு", "who are you", "TALK NAME"),
           ("என் கணக்கை வேற யாராவது பார்க்க முடியுமா", "can anyone else see my accounts", "TALK PRIVACY"),
           ("எப்படி இருக்க", "how are you", "TALK HOW_ARE_YOU"), ("உன்னை யாரு செஞ்சாங்க", "who made you", "TALK MAKER"),
           ("இன்னைக்கு மழை வருமா", "will it rain today", "NONE"), ("பஸ் எப்போ வரும்", "when will the bus come", "NONE"),
           ("பக்கத்துல ஆஸ்பத்திரி எங்க இருக்கு", "where is the hospital nearby", "NONE"), ("ரேஷன் எப்போ கொடுப்பாங்க", "when will they give ration", "NONE"),
           ("பாட்டு போடு", "play a song", "NONE"), ("ட்ரெயின் எத்தனை மணிக்கு", "what time is the train", "NONE"),
           ("என் போனுக்கு ரீசார்ஜ் பண்ணு", "recharge my phone", "NONE")],
    prefixes=[("", ""), ("அண்ணா ", "brother, "), ("ஏய் ", "hey, "), ("கேளு ", "listen, ")],
    suffixes=[("", ""), (" சொல்லு", ", tell me"), (" கொஞ்சம் சொல்லு", ", please tell me")],
)

L["en"] = dict(
    name="English",
    periods={
        "today": [("today", "today's", "today"), ("for today", "today's", "for today")],
        "week": [("this week", "this week's", "this week"), ("this whole week", "this week's", "this whole week")],
        "month": [("this month", "this month's", "this month"), ("in this month", "this month's", "in this month")],
        "year": [("this year", "this year's", "this year"), ("so far this year", "this year's", "so far this year")],
        "yesterday": [("yesterday", "yesterday's", "yesterday")],
        "last_week": [("last week", "last week's", "last week")],
        "last_month": [("last month", "last month's", "last month")],
    },
    ledger={
        "EARNED": [("how much did I earn {p}", ""), ("how much money did I make {p}", ""), ("what is my income {p}", ""),
                   ("{s} earnings please", ""), ("how much salary came {p}", ""), ("tell me {s} total earning", ""),
                   ("how much I earned {p}", ""), ("what did I get for my work {p}", ""), ("how much wages I made {p}", "")],
        "ADVANCE": [("how much advance I took {p}", ""), ("how much money did I take from the contractor {p}", ""),
                    ("how much did I borrow from the owner {p}", ""), ("{s} advance amount", ""),
                    ("how much did the maistry give me in advance {p}", ""), ("how much loan I took from seth {p}", "")],
        "EXPENSE": [("how much did I spend {p}", ""), ("what are my expenses {p}", ""), ("where did all my money go {p}", ""),
                    ("{s} spending please", ""), ("how much went on food {p}", ""), ("how much did I send home {p}", ""),
                    ("how much went on bus fare {p}", "")],
        "DUE": [("how much does the owner still have to pay me", ""), ("how much is pending {p}", ""),
                ("how much balance is due to me", ""), ("did I get my full wages {p}", ""),
                ("how much of my money is stuck with the contractor", ""), ("{s} pending amount", ""),
                ("how much more I have to get", "")],
        "ALL": [("give me {s} full account", ""), ("show {s} hisaab", ""), ("tell me everything for {p}", ""),
                ("{s} summary", ""), ("show me {s} report", ""), ("full details for {p} please", "")],
    },
    fixed=[("what is the government minimum wage", "", "ASK ANY MIN_WAGE"), ("what is the minimum daily wage", "", "ASK ANY MIN_WAGE"),
           ("how much should I get per day as per government", "", "ASK ANY MIN_WAGE"), ("what is the official wage rate", "", "ASK ANY MIN_WAGE"),
           ("what is the government rate for one day of labour", "", "ASK ANY MIN_WAGE"),
           ("what is the date today", "", "TALK DATE"), ("which day is it today", "", "TALK DATE"),
           ("what can you do", "", "TALK HELP"), ("how do I use this app", "", "TALK HELP"),
           ("what is your name", "", "TALK NAME"), ("who are you", "", "TALK NAME"),
           ("can anyone else see my accounts", "", "TALK PRIVACY"), ("how are you", "", "TALK HOW_ARE_YOU"),
           ("who made you", "", "TALK MAKER"),
           ("will it rain today", "", "NONE"), ("when will the bus come", "", "NONE"), ("where is the nearest hospital", "", "NONE"),
           ("when will we get ration", "", "NONE"), ("play a song", "", "NONE"), ("what time is the train", "", "NONE"),
           ("recharge my phone", "", "NONE")],
    prefixes=[("", ""), ("bro ", ""), ("hey ", ""), ("listen ", "")],
    suffixes=[("", ""), (" tell me", ""), (" please", "")],
)

# Words that already ask to be told or shown: no "tell me" is added after them.
IMPERATIVE = {"hi": ["बताओ", "सुनाओ", "दिखाओ", "दो"], "or": ["କୁହ", "ଦେଖାଅ", "ଶୁଣାଅ", "କହିଲ", "କହ", "ଲେଖାଜୋଖା"],
              "te": ["చెప్పు", "చూపించు"], "kn": ["ಹೇಳು", "ತೋರಿಸು", "ಕೊಡು"], "ta": ["சொல்லு", "காட்டு"],
              "en": ["tell me", "give me", "show", "please", "summary", "amount"]}

# How many of each kind per language (200 in all). Unsupported periods are about one in eight
# ledger questions; fixed questions (minimum wage, chat, not-ledger) are all used once.
LEDGER_SHARE = {"EARNED": 0.24, "ADVANCE": 0.19, "EXPENSE": 0.21, "DUE": 0.17, "ALL": 0.19}
PER_LANG = 200


def clean(t):
    return " ".join(t.split())


def build(code, rng):
    d = L[code]
    fixed = [dict(text=t, english=e or t, expect=x) for t, e, x in d["fixed"]]
    n_ledger = PER_LANG - len(fixed)
    counts = {k: round(n_ledger * v) for k, v in LEDGER_SHARE.items()}
    counts["EARNED"] += n_ledger - sum(counts.values())
    keys_supported = ["today", "week", "month", "year"]
    rows, seen = [], set()
    for topic, n in counts.items():
        frames = d["ledger"][topic]
        made, tries = 0, 0
        while made < n and tries < 20000:
            tries += 1
            text, gloss = frames[made % len(frames)] if tries <= len(frames) else rng.choice(frames)
            has_period = "{p}" in text or "{s}" in text
            if has_period:
                key = rng.choice(["yesterday", "last_week", "last_month"]) if rng.random() < 0.12 else rng.choice(keys_supported)
                adv, mod, eng = rng.choice(d["periods"][key])
                period, supported = PERIOD[key]
            else:
                adv = mod = eng = ""; period, supported = "ANY", True
            pre, pre_e = rng.choice(d["prefixes"]) if rng.random() < 0.3 else ("", "")
            # "tell me" only after a question, never after "show the report" or "tell me ...".
            suf, suf_e = rng.choice(d["suffixes"]) if rng.random() < 0.25 and not any(w in text for w in IMPERATIVE[code]) else ("", "")
            sentence = clean(pre + text.replace("{p}", adv).replace("{s}", mod) + suf)
            if sentence in seen:
                continue
            seen.add(sentence)
            if code == "en":
                english = sentence
            else:
                english = clean(pre_e + gloss.replace("{e}", eng) + suf_e)
            rows.append(dict(text=sentence, english=english, expect=f"ASK {period} {topic}", supported=supported))
            made += 1
        assert made == n, (code, topic, made, n)
    for f in fixed:
        f["supported"] = True
    allrows = rows + fixed
    rng.shuffle(allrows)
    for i, r in enumerate(allrows, 1):
        r["id"] = f"{code}_{i:03d}"
        r["lang"] = code
    return allrows


def describe(expect, lang_name):
    kind, *rest = expect.split()
    if kind == "NONE":
        return "Not about the ledger: should say what the app can do"
    if kind == "TALK":
        return {"DATE": "Today's date", "HELP": "What the app can do", "NAME": "Its name", "PRIVACY": "That records stay on the phone",
                "HOW_ARE_YOU": "A friendly reply", "MAKER": "Who made it"}[rest[0]]
    period, topic = rest
    t = {"EARNED": "money earned", "ADVANCE": "advance taken", "EXPENSE": "money spent", "DUE": "money still owed to the worker",
         "ALL": "full summary", "MIN_WAGE": "government minimum wage"}[topic]
    p = {"DAY": "today", "WEEK": "this week", "MONTH": "this month", "YEAR": "this year", "ANY": "",
         "YESTERDAY": "yesterday", "LAST_WEEK": "last week", "LAST_MONTH": "last month"}[period]
    return (t[0].upper() + t[1:] + (f", {p}" if p else "")).strip()


def main():
    rng = random.Random(2026)
    rows = [r for code in ["hi", "or", "te", "kn", "ta", "en"] for r in build(code, rng)]
    with open("core/src/test/resources/voice_questions.jsonl", "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps({k: r[k] for k in ["id", "lang", "text", "english", "expect", "supported"]}, ensure_ascii=False) + "\n")
    print(len(rows), "questions")
    if len(sys.argv) > 1:
        write_xlsx(rows, sys.argv[1])


def write_xlsx(rows, path):
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Font, PatternFill, Border, Side
    wb = Workbook()
    head = Font(name="Arial", bold=True, color="FFFFFF", size=11)
    fill = PatternFill("solid", fgColor="1E5631")
    body = Font(name="Arial", size=12)
    small = Font(name="Arial", size=10, color="555555")
    thin = Border(bottom=Side(style="thin", color="DDDDDD"))
    wrap = Alignment(wrap_text=True, vertical="top")

    ws = wb.active
    ws.title = "How to record"
    guide = [
        ("HisaabSaathi voice question test", True),
        ("200 questions in each language, asked the way workers really speak. Record each one, then send the recordings back to be run through the app.", False),
        ("", False),
        ("1. Use the speaker's own phone voice recorder. Hold the phone as you would to talk to the app (about 20 cm away).", False),
        ("2. Read the sentence in column B naturally, the way you would say it at work. Small changes in wording are fine: write what you actually said in column G.", False),
        ("3. Save each recording with the name in column F (for example hi_001.m4a). Any format the phone records (m4a, mp3, wav, ogg) is fine.", False),
        ("4. Record some in a quiet room and some outdoors or with background noise. Mark which in column H (quiet / noisy).", False),
        ("5. More speakers are better: men and women, young and old, different districts. Put the speaker's initials in column I.", False),
        ("6. Put all recordings of one language in one folder (hi, or, te, kn, ta, en), zip them and send them back.", False),
        ("", False),
        ("Only fill in columns G, H and I (shaded yellow). Columns A–F are already filled.", True),
        ("Column D says what the app should answer. 'Supported now: no' marks questions about yesterday, last week or last month, which the app cannot answer yet: they show how often workers ask them.", False),
        ("", False),
        ("Example row (from the Hindi sheet):", True),
    ]
    for i, (t, bold) in enumerate(guide, 1):
        c = ws.cell(row=i, column=1, value=t)
        c.font = Font(name="Arial", size=14 if i == 1 else 11, bold=bold)
        c.alignment = Alignment(wrap_text=True, vertical="top")
    ws.column_dimensions["A"].width = 120
    ex = len(guide) + 1
    example = ["1", "इस हफ्ते मैंने कितना कमाया", "how much did I earn this week", "Money earned, this week", "yes", "hi_001.m4a", "इस हफ्ते कितना कमाया", "quiet", "RK"]
    ws.cell(row=ex, column=1, value=" | ".join(example)).font = Font(name="Arial", size=11, italic=True)

    cols = [("No.", 6), ("Sentence to say", 46), ("English meaning", 42), ("App should answer", 34), ("Supported now", 11),
            ("Save recording as", 18), ("What was actually said (if different)", 36), ("Quiet / noisy", 12), ("Speaker", 10)]
    yellow = PatternFill("solid", fgColor="FFF4C2")
    names = {c: L[c]["name"] for c in L}
    for code in ["hi", "or", "te", "kn", "ta", "en"]:
        ws = wb.create_sheet(names[code])
        for j, (h, w) in enumerate(cols, 1):
            c = ws.cell(row=1, column=j, value=h); c.font = head; c.fill = fill; c.alignment = Alignment(wrap_text=True, vertical="center")
            ws.column_dimensions[chr(64 + j)].width = w
        ws.row_dimensions[1].height = 32
        ws.freeze_panes = "B2"
        for i, r in enumerate([r for r in rows if r["lang"] == code], 2):
            vals = [i - 1, r["text"], r["english"], describe(r["expect"], names[code]), "yes" if r["supported"] else "no", r["id"] + ".m4a", None, None, None]
            for j, v in enumerate(vals, 1):
                c = ws.cell(row=i, column=j, value=v)
                c.font = body if j == 2 else (small if j in (3, 4, 5, 6) else Font(name="Arial", size=11))
                c.alignment = wrap; c.border = thin
                if j >= 7:
                    c.fill = yellow
    wb.save(path)
    print("wrote", path)


if __name__ == "__main__":
    main()
