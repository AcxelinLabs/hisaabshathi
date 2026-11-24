# Setup questions to record

HisaabSaathi reads these aloud during first-time setup. With recordings in the app, the questions are spoken from the first screen, before any download. Without them, the app uses its own voice once the voice pack has downloaded, or the phone's voice.

**Quickest: generate them.** From the `android` folder run `python tools/tts/make_onboarding_audio.py` (needs `pip install numpy onnxruntime`). It speaks every sentence below with the app's own voices for Hindi, Odia, Telugu, Kannada and Tamil and saves `.wav` files in the right folders. English needs no files: every phone has an English voice.

**Better later: record a native speaker.** **How to record:** a native speaker reads each line once, calmly and clearly, in a quiet room (a phone's voice recorder is fine). Save each as MP3 (or M4A/OGG/WAV) with the exact file name shown, and put it in
`android/app/src/main/assets/onboarding_audio/<code>/`, for example `onboarding_audio/or/q_name.mp3`. Rebuild the app; nothing else to change.

If you change a sentence here, change it in `core/Strings.kt` too (and the Telugu, Kannada and Tamil tables), so the screen and the voice say the same thing.

## Hindi (हिन्दी) — folder `onboarding_audio/hi/`

| File | Say this |
|---|---|
| `q_photo.mp3` | अपनी फोटो लें. फोन को अपने चेहरे के सामने रखें और सेल्फ़ी लें। फोटो सिर्फ़ इसी फोन में रहती है। |
| `details_privacy.mp3` | आपकी जानकारी इसी फोन में ताले में रहती है। कोई और इसे नहीं देख सकता। कुछ भी अपलोड नहीं होता। |
| `q_name.mp3` | आपका नाम क्या है? |
| `q_age.mp3` | आपकी उम्र कितनी है? |
| `q_pin.mp3` | आपका पिन कोड क्या है? इसमें 6 अंक होते हैं। |
| `check_details.mp3` | अपनी जानकारी देखें। कुछ गलत हो तो बदलें। |
| `q_address.mp3` | क्या आप अपना पता बताना चाहेंगे? |
| `q_location.mp3` | क्या फ़ोन की लोकेशन से पता भर दें? |
| `q_state.mp3` | आप किस राज्य में रहते हैं? |
| `q_district.mp3` | आपका ज़िला कौन सा है? |
| `q_village.mp3` | आपके गाँव या शहर का नाम क्या है? |
| `q_pin_known.mp3` | पिन कोड पता हो तो बताइए। नहीं पता, तो कोई बात नहीं। |

## Odia (ଓଡ଼ିଆ) — folder `onboarding_audio/or/`

| File | Say this |
|---|---|
| `q_photo.mp3` | ଆପଣଙ୍କ ଫଟୋ ନିଅନ୍ତୁ. ଫୋନ୍‌କୁ ମୁହଁ ସାମ୍ନାରେ ଧରି ସେଲ୍ଫି ନିଅନ୍ତୁ। ଫଟୋ କେବଳ ଏହି ଫୋନ୍‌ରେ ରହେ। |
| `details_privacy.mp3` | ଆପଣଙ୍କ ବିବରଣୀ ଏହି ଫୋନ୍‌ରେ ତାଲା ପଡ଼ି ରହେ। ଆଉ କେହି ଦେଖିପାରିବେ ନାହିଁ। କିଛି ଅପଲୋଡ୍ ହୁଏ ନାହିଁ। |
| `q_name.mp3` | ଆପଣଙ୍କ ନାମ କଣ? |
| `q_age.mp3` | ଆପଣଙ୍କ ବୟସ କେତେ? |
| `q_pin.mp3` | ଆପଣଙ୍କ ପିନ୍ କୋଡ୍ କଣ? ଏଥିରେ 6ଟି ଅଙ୍କ ଥାଏ। |
| `check_details.mp3` | ଆପଣଙ୍କ ବିବରଣୀ ଦେଖନ୍ତୁ। କିଛି ଭୁଲ୍ ଥିଲେ ବଦଳାନ୍ତୁ। |
| `q_address.mp3` | ଆପଣ ନିଜ ଠିକଣା କହିବାକୁ ଚାହିଁବେ କି? |
| `q_location.mp3` | ଫୋନ୍‌ର ଲୋକେସନ୍‌ରୁ ଠିକଣା ଭରିଦେବୁ କି? |
| `q_state.mp3` | ଆପଣ କେଉଁ ରାଜ୍ୟରେ ରହନ୍ତି? |
| `q_district.mp3` | ଆପଣଙ୍କ ଜିଲ୍ଲା କେଉଁଟି? |
| `q_village.mp3` | ଆପଣଙ୍କ ଗାଁ ବା ସହରର ନାମ କଣ? |
| `q_pin_known.mp3` | ପିନ୍ କୋଡ୍ ଜାଣିଥିଲେ କୁହନ୍ତୁ। ନ ଜାଣିଲେ କିଛି ଅସୁବିଧା ନାହିଁ। |

## Telugu (తెలుగు) — folder `onboarding_audio/te/`

| File | Say this |
|---|---|
| `q_photo.mp3` | మీ ఫోటో తీయండి. ఫోన్‌ను మీ ముఖం ముందు పట్టుకుని సెల్ఫీ తీయండి. ఫోటో ఈ ఫోన్‌లోనే ఉంటుంది. |
| `details_privacy.mp3` | మీ వివరాలు ఈ ఫోన్‌లోనే తాళం వేసి ఉంటాయి. వేరెవరూ చూడలేరు. ఏదీ అప్‌లోడ్ కాదు. |
| `q_name.mp3` | మీ పేరు ఏమిటి? |
| `q_age.mp3` | మీ వయసు ఎంత? |
| `q_pin.mp3` | మీ పిన్ కోడ్ ఏమిటి? అందులో 6 అంకెలు ఉంటాయి. |
| `check_details.mp3` | మీ వివరాలు చూడండి. ఏదైనా తప్పుగా ఉంటే మార్చండి. |
| `q_address.mp3` | మీ చిరునామా చెబుతారా? |
| `q_location.mp3` | ఫోన్ లొకేషన్‌తో చిరునామా నింపమంటారా? |
| `q_state.mp3` | మీరు ఏ రాష్ట్రంలో ఉంటారు? |
| `q_district.mp3` | మీ జిల్లా ఏది? |
| `q_village.mp3` | మీ ఊరు లేదా పట్టణం పేరు ఏమిటి? |
| `q_pin_known.mp3` | పిన్ కోడ్ తెలిస్తే చెప్పండి. తెలియకపోతే పర్వాలేదు. |

## Kannada (ಕನ್ನಡ) — folder `onboarding_audio/kn/`

| File | Say this |
|---|---|
| `q_photo.mp3` | ನಿಮ್ಮ ಫೋಟೋ ತೆಗೆಯಿರಿ. ಫೋನನ್ನು ನಿಮ್ಮ ಮುಖದ ಮುಂದೆ ಹಿಡಿದು ಸೆಲ್ಫಿ ತೆಗೆಯಿರಿ. ಫೋಟೋ ಈ ಫೋನ್‌ನಲ್ಲೇ ಇರುತ್ತದೆ. |
| `details_privacy.mp3` | ನಿಮ್ಮ ವಿವರಗಳು ಈ ಫೋನ್‌ನಲ್ಲೇ ಬೀಗ ಹಾಕಿ ಇರುತ್ತವೆ. ಬೇರೆ ಯಾರೂ ನೋಡಲಾರರು. ಏನೂ ಅಪ್‌ಲೋಡ್ ಆಗುವುದಿಲ್ಲ. |
| `q_name.mp3` | ನಿಮ್ಮ ಹೆಸರೇನು? |
| `q_age.mp3` | ನಿಮ್ಮ ವಯಸ್ಸು ಎಷ್ಟು? |
| `q_pin.mp3` | ನಿಮ್ಮ ಪಿನ್ ಕೋಡ್ ಏನು? ಅದರಲ್ಲಿ 6 ಅಂಕೆಗಳಿವೆ. |
| `check_details.mp3` | ನಿಮ್ಮ ವಿವರಗಳನ್ನು ನೋಡಿ. ಏನಾದರೂ ತಪ್ಪಿದ್ದರೆ ಬದಲಿಸಿ. |
| `q_address.mp3` | ನಿಮ್ಮ ವಿಳಾಸ ಹೇಳುತ್ತೀರಾ? |
| `q_location.mp3` | ಫೋನ್ ಲೊಕೇಶನ್‌ನಿಂದ ವಿಳಾಸ ತುಂಬಿಸೋಣವೇ? |
| `q_state.mp3` | ನೀವು ಯಾವ ರಾಜ್ಯದಲ್ಲಿ ಇರುತ್ತೀರಿ? |
| `q_district.mp3` | ನಿಮ್ಮ ಜಿಲ್ಲೆ ಯಾವುದು? |
| `q_village.mp3` | ನಿಮ್ಮ ಊರು ಅಥವಾ ಪಟ್ಟಣದ ಹೆಸರೇನು? |
| `q_pin_known.mp3` | ಪಿನ್ ಕೋಡ್ ಗೊತ್ತಿದ್ದರೆ ಹೇಳಿ. ಗೊತ್ತಿಲ್ಲದಿದ್ದರೆ ಪರವಾಗಿಲ್ಲ. |

## Tamil (தமிழ்) — folder `onboarding_audio/ta/`

| File | Say this |
|---|---|
| `q_photo.mp3` | உங்கள் புகைப்படத்தை எடுங்கள். போனை உங்கள் முகத்தின் முன் பிடித்து செல்ஃபி எடுங்கள். புகைப்படம் இந்த போனில் மட்டுமே இருக்கும். |
| `details_privacy.mp3` | உங்கள் விவரங்கள் இந்த போனிலேயே பூட்டப்பட்டு இருக்கும். வேறு யாரும் பார்க்க முடியாது. எதுவும் பதிவேற்றப்படாது. |
| `q_name.mp3` | உங்கள் பெயர் என்ன? |
| `q_age.mp3` | உங்கள் வயது என்ன? |
| `q_pin.mp3` | உங்கள் பின் கோடு என்ன? அதில் 6 இலக்கங்கள் இருக்கும். |
| `check_details.mp3` | உங்கள் விவரங்களைப் பாருங்கள். ஏதாவது தவறாக இருந்தால் மாற்றுங்கள். |
| `q_address.mp3` | உங்கள் முகவரியைச் சொல்கிறீர்களா? |
| `q_location.mp3` | போனின் இருப்பிடத்தை வைத்து முகவரியை நிரப்பட்டுமா? |
| `q_state.mp3` | நீங்கள் எந்த மாநிலத்தில் இருக்கிறீர்கள்? |
| `q_district.mp3` | உங்கள் மாவட்டம் எது? |
| `q_village.mp3` | உங்கள் ஊர் அல்லது நகரத்தின் பெயர் என்ன? |
| `q_pin_known.mp3` | பின் கோடு தெரிந்தால் சொல்லுங்கள். தெரியாவிட்டால் பரவாயில்லை. |

## English (English) — folder `onboarding_audio/en/`

| File | Say this |
|---|---|
| `q_photo.mp3` | Take your photo. Hold the phone in front of your face and take a selfie. The photo stays only on this phone. |
| `details_privacy.mp3` | Your details stay on this phone, locked. Nobody else can see them. Nothing is uploaded. |
| `q_name.mp3` | What is your name? |
| `q_age.mp3` | How old are you? |
| `q_pin.mp3` | What is your PIN code? It has 6 digits. |
| `check_details.mp3` | Check your details. Change anything that is wrong. |

