package com.acxelin.hisaabsaathi.core

/** Which meal some food was for, when the worker says so. */
enum class Meal { BREAKFAST, LUNCH, DINNER }

/** What a loan was for, when the worker says so ("bike loan", "गोल्ड लोन की किस्त"). */
enum class LoanKind { CAR, BIKE, PHONE, HOUSE, GOLD, TRACTOR, GROUP }

/**
 * The small things said with an amount that make a line easy to recognise later: the food
 * ("समोसा, चाय"), the meal ("नाश्ता"), what a loan was for ("बाइक"). Kept on the line as a short
 * code ("food=SAMOSA,TEA;meal=BREAKFAST", "loan=BIKE") and shown in the worker's language.
 * Words are matched exactly (after [Text.normalize]), never loosely: a wrong item is worse
 * than none.
 */
data class Detail(
    val foods: List<String> = emptyList(),
    val meal: Meal? = null,
    /** True for any loan; [loanKind] says what for, when that was said. */
    val loan: Boolean = false,
    val loanKind: LoanKind? = null,
) {
    val isEmpty get() = foods.isEmpty() && meal == null && !loan

    fun encode(): String = listOfNotNull(
        foods.takeIf { it.isNotEmpty() }?.joinToString(",", prefix = "food="),
        meal?.let { "meal=${it.name}" },
        if (loan) "loan=${loanKind?.name.orEmpty()}" else null,
    ).joinToString(";")

    companion object {
        fun decode(code: String): Detail {
            if (code.isBlank()) return Detail()
            val parts = code.split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            return Detail(
                foods = parts["food"]?.split(',')?.filter { it in Items.foodKeys }.orEmpty(),
                meal = parts["meal"]?.let { m -> Meal.entries.firstOrNull { it.name == m } },
                loan = "loan" in parts,
                loanKind = parts["loan"]?.let { k -> LoanKind.entries.firstOrNull { it.name == k } },
            )
        }
    }
}

object Items {
    /** One food: its name in hi, or, te, kn, ta, en (the [Lang] order), and other ways people say it. */
    private class Food(val key: String, val names: List<String>, val said: String = "")

    private val foods = listOf(
        Food("TEA", listOf("चाय", "ଚା", "టీ", "ಟೀ", "டீ", "Tea"), "chai chay tea चाय ఛాయ్ చాయ్ ಚಹಾ ಚಾ தேநீர் டீ"),
        Food("COFFEE", listOf("कॉफ़ी", "କଫି", "కాఫీ", "ಕಾಫಿ", "காபி", "Coffee"), "coffee kafi kaafi कॉफी"),
        Food("SAMOSA", listOf("समोसा", "ସିଙ୍ଗଡ଼ା", "సమోసా", "ಸಮೋಸ", "சமோசா", "Samosa"), "samosa samose samosas समोसे ସମୋସା ସିଙ୍ଗଡା ಸಮೋಸಾ"),
        Food("PAKODA", listOf("पकौड़ा", "ପକୋଡ଼ି", "బజ్జీ", "ಬಜ್ಜಿ", "பஜ்ஜி", "Pakoda"), "pakoda pakora pakode pakore पकौड़े पकोड़ा पकोड़े पकौडा bajji bhajji ପକୋଡି పకోడీ ಪಕೋಡ பக்கோடா"),
        Food("VADA", listOf("वड़ा", "ବରା", "వడ", "ವಡೆ", "வடை", "Vada"), "vada vade wada vadai वडा वड़े ବରା ବଡ଼ା వడలు"),
        Food("IDLI", listOf("इडली", "ଇଡ଼ଲି", "ఇడ్లీ", "ಇಡ್ಲಿ", "இட்லி", "Idli"), "idli idly ଇଡଲି"),
        Food("DOSA", listOf("डोसा", "ଦୋସା", "దోశ", "ದೋಸೆ", "தோசை", "Dosa"), "dosa dosai dose दोसा దోస"),
        Food("UPMA", listOf("उपमा", "ଉପମା", "ఉప్మా", "ಉಪ್ಪಿಟ್ಟು", "உப்புமா", "Upma"), "upma uppittu"),
        Food("POHA", listOf("पोहा", "ଚୁଡ଼ା", "అటుకులు", "ಅವಲಕ್ಕಿ", "அவல்", "Poha"), "poha chuda chura ଚୁଡା avalakki atukulu aval"),
        Food("PARATHA", listOf("पराठा", "ପରଟା", "పరోటా", "ಪರೋಟ", "பரோட்டா", "Paratha"), "paratha parantha parotta parota पराँठा पराठे परांठा ପରୋଟା ಪರೋಟಾ"),
        Food("ROTI", listOf("रोटी", "ରୁଟି", "చపాతీ", "ಚಪಾತಿ", "சப்பாத்தி", "Roti"), "roti rotis chapati chapathi chapatti रोटियां चपाती ରୋଟି ರೊಟ್ಟಿ రొట్టె ரொட்டி"),
        Food("PURI", listOf("पूड़ी", "ପୁରି", "పూరీ", "ಪೂರಿ", "பூரி", "Puri"), "puri poori पूरी पूड़ियां ପୁରୀ"),
        Food("RICE", listOf("चावल", "ଭାତ", "అన్నం", "ಅನ್ನ", "சாதம்", "Rice"), "chawal chaval bhaat bhat rice भात ಅನ್ನ சோறு"),
        Food("DAL", listOf("दाल", "ଡାଲି", "పప్పు", "ಬೇಳೆ", "பருப்பு", "Dal"), "dhal"),
        Food("SABZI", listOf("सब्ज़ी", "ତରକାରି", "కూరగాయలు", "ತರಕಾರಿ", "காய்கறி", "Vegetables"), "sabzi sabji सब्जी सब्जियां tarkari ତରକାରୀ ପରିବା vegetables vegetable ಪಲ್ಯ"),
        Food("THALI", listOf("थाली", "ଥାଳି", "మీల్స్", "ಊಟದ ತಟ್ಟೆ", "மீல்ஸ்", "Meal plate"), "thali meals मील्स ଥାଳୀ"),
        Food("BIRYANI", listOf("बिरयानी", "ବିରିୟାନି", "బిర్యానీ", "ಬಿರಿಯಾನಿ", "பிரியாணி", "Biryani"), "biryani biriyani"),
        Food("KHICHDI", listOf("खिचड़ी", "ଖେଚୁଡ଼ି", "కిచిడీ", "ಕಿಚಡಿ", "கிச்சடி", "Khichdi"), "khichdi khichri ଖେଚୁଡି"),
        Food("EGG", listOf("अंडा", "ଅଣ୍ଡା", "గుడ్డు", "ಮೊಟ್ಟೆ", "முட்டை", "Egg"), "anda ande egg eggs अंडे omelette omelet ऑमलेट आमलेट గుడ్లు"),
        Food("CHICKEN", listOf("चिकन", "ଚିକେନ୍", "చికెన్", "ಚಿಕನ್", "சிக்கன்", "Chicken"), "chicken murga मुर्गा ଚିକେନ"),
        Food("FISH", listOf("मछली", "ମାଛ", "చేప", "ಮೀನು", "மீன்", "Fish"), "machhli machli fish macha చేపలు"),
        Food("MUTTON", listOf("मटन", "ମଟନ୍", "మటన్", "ಮಟನ್", "மட்டன்", "Mutton"), "mutton gosht गोश्त ମାଂସ ମଟନ"),
        Food("BISCUIT", listOf("बिस्कुट", "ବିସ୍କୁଟ୍", "బిస్కెట్", "ಬಿಸ್ಕತ್", "பிஸ்கட்", "Biscuits"), "biscuit biscuits biskut ବିସ୍କୁଟ"),
        Food("BREAD", listOf("ब्रेड", "ପାଉଁରୁଟି", "బ్రెడ్", "ಬ್ರೆಡ್", "பிரெட்", "Bread"), "bread pav पाव bun बन"),
        Food("MILK", listOf("दूध", "କ୍ଷୀର", "పాలు", "ಹಾಲು", "பால்", "Milk"), "doodh dudh milk"),
        Food("FRUIT", listOf("फल", "ଫଳ", "పండ్లు", "ಹಣ್ಣು", "பழம்", "Fruit"), "phal fruit fruits kela केला केले banana bananas କଦଳୀ అరటి ಬಾಳೆಹಣ್ಣು வாழைப்பழம்"),
        Food("SWEETS", listOf("मिठाई", "ମିଠା", "స్వీట్", "ಸಿಹಿ", "இனிப்பு", "Sweets"), "mithai sweet sweets jalebi जलेबी laddu लड्डू ଲଡୁ ରସଗୋଲା లడ్డు ಲಾಡು லட்டு"),
        Food("NAMKEEN", listOf("नमकीन", "ମିକ୍ସଚର୍", "మిక్చర్", "ಮಿಕ್ಸ್ಚರ್", "மிக்சர்", "Namkeen"), "namkeen mixture bhujia भुजिया ମିକ୍ସଚର"),
        Food("COLDDRINK", listOf("कोल्ड ड्रिंक", "କୋଲ୍ଡ ଡ୍ରିଙ୍କ୍", "కూల్ డ్రింక్", "ಕೂಲ್ ಡ್ರಿಂಕ್", "கூல் டிரிங்க்", "Cold drink"), "colddrink cooldrink soda सोडा"),
    )
    val foodKeys: Set<String> = foods.map { it.key }.toSet()

    private fun table(pairs: List<Pair<String, String>>): Map<String, String> =
        pairs.flatMap { (key, words) -> words.split(' ').filter { it.isNotBlank() }.map { Text.normalize(it) to key } }.toMap()

    private val foodWords: Map<String, String> = table(foods.map { f -> f.key to (f.names.joinToString(" ") + " " + f.said) })
    /** "cold drink", "कोल्ड ड्रिंक": the two words together. */
    private val foodPairs: Map<String, String> = foods.flatMap { f -> f.names.filter { ' ' in it }.map { Text.normalize(it.replace(" ", "")) to f.key } }.toMap() +
        mapOf("colddrink" to "COLDDRINK", "cooldrink" to "COLDDRINK")

    private val mealWords: Map<String, String> = table(listOf(
        Meal.BREAKFAST.name to "नाश्ता नाश्ते नास्ता nashta nasta naashta breakfast ଜଳଖିଆ ଜଳଖିଆରେ tiffin tifin टिफिन టిఫిన్ అల్పాహారం ತಿಂಡಿ ಉಪಾಹಾರ டிபன் டிஃபன் சிற்றுண்டி",
        Meal.LUNCH.name to "दोपहर dopahar dophar lunch लंच ମଧ୍ୟାହ୍ନ ମଧ୍ୟାହ୍ନରେ ଦ୍ୱିପହର ଦିପହର మధ్యాహ్నం మధ్యాహ్న ಮಧ್ಯಾಹ್ನ ಮಧ್ಯಾಹ್ನದ மதியம் மதிய",
        Meal.DINNER.name to "रात raat dinner डिनर ରାତି ରାତିର ରାତିରେ రాత్రి ರಾತ್ರಿ ರಾತ್ರಿಯ இரவு ராத்திரி",
    ))

    private val loanWords: Set<String> = "लोन loan loans कर्ज कर्ज़ कर्जा karz karza qarz ଋଣ ଲୋନ୍ ଲୋନ అప్పు లోన్ ಸಾಲ ಲೋನ್ கடன் லோன்"
        .split(' ').map(Text::normalize).toSet()

    /** Paying a loan back, rather than taking one: the line is spending, not an advance. */
    private val repayWords: Set<String> = ("किस्त किश्त किस्तें kist kisht qist emi ईएमआई installment instalment चुकाया चुकाई चुकाए chukaya chukai " +
        "repaid repay ମାସିକିସ୍ତି କିସ୍ତି ଶୁଝିଲି కిస్తీ వాయిదా ఈఎంఐ ಕಂತು ಇಎಂಐ தவணை இஎம்ஐ")
        .split(' ').map(Text::normalize).toSet()

    private val loanKindWords: Map<String, String> = table(listOf(
        LoanKind.CAR.name to "car कार କାର కారు ಕಾರು கார்",
        LoanKind.BIKE.name to "bike बाइक motorcycle scooter scooty स्कूटर स्कूटी मोटरसाइकिल ବାଇକ୍ ବାଇକ ସ୍କୁଟି బైక్ ಬೈಕ್ பைக் ஸ்கூட்டி",
        LoanKind.PHONE.name to "phone फोन फ़ोन mobile मोबाइल ଫୋନ୍ ମୋବାଇଲ୍ ఫోన్ మొబైల్ ಫೋನ್ ಮೊಬೈಲ್ போன் மொபைல்",
        LoanKind.HOUSE.name to "house home ghar घर मकान makan ଘର ఇల్లు ఇంటి ಮನೆ வீடு வீட்டு",
        LoanKind.GOLD.name to "gold गोल्ड सोना सोने ସୁନା ଗୋଲ୍ଡ బంగారం గోల్డ్ ಚಿನ್ನ ಚಿನ್ನದ ಗೋಲ್ಡ್ தங்கம் நகை",
        LoanKind.TRACTOR.name to "tractor ट्रैक्टर ଟ୍ରାକ୍ଟର ట్రాక్టర్ ಟ್ರ್ಯಾಕ್ಟರ್ டிராக்டர்",
        LoanKind.GROUP.name to "group shg समूह ସମୂହ ଗୋଷ୍ଠୀ సంఘం ಸಂಘ ಸಂಘದ குழு",
    ))

    fun food(word: String): String? = foodWords[word]
    fun foodPair(a: String, b: String): String? = foodPairs[a + b]
    fun meal(word: String): Meal? = mealWords[word]?.let(Meal::valueOf)
    fun isLoan(word: String): Boolean = word in loanWords
    fun isRepay(word: String): Boolean = word in repayWords
    fun loanKind(word: String): LoanKind? = loanKindWords[word]?.let(LoanKind::valueOf)

    /** Every word that names a food: for telling food spending from other spending. */
    val foodWordSet: Set<String> get() = foodWords.keys

    fun foodName(key: String, lang: Lang): String = foods.firstOrNull { it.key == key }?.names?.get(lang.ordinal) ?: key
}
