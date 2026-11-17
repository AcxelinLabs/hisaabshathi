#!/usr/bin/env python3
"""HisaabSaathi demo video, version 2.

Layout (1920x1080, the same for the whole video):
  left   - the app on a phone, crossfading between stages
  right  - stage title and a plain-language explanation of what is happening
  middle - subtitles of what the speaker is saying
  corner - the speaker's face in a fixed circular webcam overlay, bottom right
Pauses between sentences are shortened and speech is sped up slightly (pitch kept) so the
three recordings fit in two minutes without cutting anyone's words."""
import os, subprocess, textwrap
from PIL import Image, ImageDraw, ImageFont

D = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(D, "build2"); os.makedirs(OUT, exist_ok=True)
SCREENS = "/home/user/hisaabshathi/android/app/build/screens"
FONTS = "/home/user/hisaabshathi/android/app/src/main/res/font"
W, H, FPS = 1920, 1080, 30
INK, CHALK, MARIGOLD, PAPER, SOFT = (20, 67, 42), (239, 244, 238), (242, 169, 0), (255, 255, 255), (79, 107, 90)
TARGET = 119.0
XF = 0.5                      # crossfade between stages, seconds
CAM_D, CAM_X, CAM_Y = 250, 1630, 790   # webcam circle: diameter and top-left corner
RIGHT_X, RIGHT_W = 860, 980   # right-hand text column

def font(w, s): return ImageFont.truetype(f"{FONTS}/anek_latin_{w}.ttf", s)
def run(cmd): subprocess.run(cmd, check=True)

# Speech segments in each original recording: (start, end, spoken caption chunks)
SPEAKERS = [
    dict(key="monish", src="v1_VID_20261118_001458720.mp4", name="Monish", role="Software Engineer", crop=(580, 40, 640), segs=[
        (1.0, 5.1, ["Hi, I'm Monish, a software engineer at Acxelin Labs."]),
        (5.5, 18.5, ["This is HisaabSaathi, a voice ledger for daily-wage workers.",
                     "A worker first picks their language: Hindi, Odia, Telugu, Kannada, Tamil or English.", "Then their state."]),
        (19.1, 29.5, ["After that, there's one big button.", "The worker simply says what happened today, something like:",
                      "\"Aaj aath ghante kaam, ek sau bees piece, paanch rupaye rate...\""]),
        (29.9, 31.1, ["\"...teen sau advance.\""]),
        (31.7, 36.8, ["The app understands this on the phone itself, with no internet, and reads it back."]),
        (37.4, 40.6, ["Nothing is saved until the worker taps the green tick."]),
        (41.8, 42.3, ["Now today's card..."]), (42.8, 43.6, ["Now today's card..."]),
        (44.1, 54.4, ["...shows ₹600 earned and ₹300 taken as advance,", "and ₹300 still owed by the contractor.",
                      "Every record is encrypted and stays on this phone."]),
    ], stages=[
        (0.0, "01-language.png", "Choose a language", "Hindi, Odia, Telugu, Kannada, Tamil or English. The whole app, including its voice, switches to it."),
        (16.5, "02-state.png", "Choose the work state", "Used to look up the official minimum wage for that state."),
        (19.1, "04-home-empty.png", "One big button", "Today's wage slip sits on top. The worker taps the mic and speaks. No typing, no forms."),
        (31.7, "05-confirm.png", "Check before saving", "Understood on the phone, offline, and read aloud. Nothing is saved until the worker taps the green tick."),
        (41.8, "06-home-after-save.png", "Saved, with totals", "₹600 earned, ₹300 advance, ₹300 still owed. Encrypted and kept only on this phone."),
    ]),
    dict(key="aditya", src="v2_whatsapp_20261118.mp4", name="Aditya", role="Software Engineer", crop=(97, 110, 400), segs=[
        (2.8, 3.2, ["Hi, I'm Aditya, working at Acxelin Labs, and I'm originally from Odisha."]),
        (3.6, 7.6, ["Hi, I'm Aditya, working at Acxelin Labs, and I'm originally from Odisha."]),
        (8.0, 10.9, ["I work on how the app listens and speaks."]),
        (11.3, 12.3, ["Workers can ask questions in their own language,"]),
        (12.6, 16.0, ["like \"Is hafte kitna kamaya?\""]),
        (16.3, 19.1, ["(How much did I earn this week?) and hear the answer."]),
        (19.5, 24.2, ["The summary shows the week, month or year.", "It can read it aloud or share it as a PDF."]),
        (24.8, 36.0, ["The wage screen compares their average daily pay with the official minimum wage for their state.",
                      "If they are underpaid, they can create an evidence file for a union or labour office.",
                      "And here is the same app in Odia."]),
    ], stages=[
        (0.0, "08-ledger.png", "Every entry, day by day", "Work, advances, wages received and expenses, each with its own picture."),
        (11.3, "22-answer-hi.png", "Ask a question", "Asked in Hindi: \"How much did I earn this week?\" The app shows what it heard, then answers on screen and aloud."),
        (19.5, "09-summary.png", "Weekly and monthly summary", "Earnings against spending. Read it aloud or share it as a PDF."),
        (24.8, "10-wage.png", "Minimum-wage check", "Average daily pay against the official rate for the state, plus a tamper-evident evidence file."),
        (33.2, "22-answer-or.png", "The same app in Odia", "The same question, asked and answered in Odia."),
    ]),
    dict(key="ranjan", src="v3_WIN_20261117_125449.mp4", name="Ranjan", role="Research Lead", crop=(366, 170, 520), segs=[
        (0.4, 1.0, ["My name is Ranjan, and I lead research at Acxelin Labs."]),
        (1.4, 11.4, ["My name is Ranjan, and I lead research at Acxelin Labs.",
                     "Today, the contractor holds the only record of workers' hours and pay in India."]),
        (12.1, 18.3, ["The HisaabSaathi team's vision: we want every worker to hold their own record,"]),
        (18.7, 22.7, ["on a cheap phone, in their own language,"]), (23.3, 25.4, ["without handing their data to anyone."]),
        (26.1, 27.7, ["Our AI models are small and open,"]), (28.0, 34.9, ["and they run on these cheap phones, so the app stays free."]),
        (35.7, 36.9, ["Next, we are running pilots on construction sites,"]),
        (37.6, 44.4, ["Next, we are running pilots on construction sites,", "adding more languages, and building an iOS version,"]),
        (44.9, 46.3, ["targeting more workers. Thank you."]), (47.5, 49.3, ["targeting more workers. Thank you."]),
    ], stages=[
        (0.0, "22-answer-te.png", "Why it matters", "Today the contractor holds the only record of hours and pay. Shown here in Telugu."),
        (12.1, "22-answer-ta.png", "Our vision", "Every worker holds their own record: on a cheap phone, in their own language, shared with no one. Shown in Tamil."),
        (26.1, "22-answer-kn.png", "Small, open AI", "Speech and language models run on the phone. No internet, no cloud bills, free for workers. Shown in Kannada."),
        (35.7, "20-home-en.png", "What's next", "Pilots on construction sites, more languages, and an iOS version."),
        (44.9, None, "", ""),  # closing card
    ]),
]
PAD = 0.15

def keep_ranges(sp):
    rs = []
    for s, e, _ in sp["segs"]:
        a, b = max(0.0, s - PAD), e + PAD
        if rs and a <= rs[-1][1]: rs[-1][1] = max(rs[-1][1], b)
        else: rs.append([a, b])
    return rs

total = sum(b - a for sp in SPEAKERS for a, b in keep_ranges(sp))
SPEED = max(1.0, total / TARGET)
print(f"speech kept {total:.1f}s -> speed x{SPEED:.3f}")

def mapper(rs, offset):
    def f(t):
        acc = 0.0
        for a, b in rs:
            if t <= a: return offset + acc / SPEED
            if t <= b: return offset + (acc + t - a) / SPEED
            acc += b - a
        return offset + acc / SPEED
    return f

# ---------------------------------------------------------------- graphics
def phone(png, height):
    shot = Image.open(f"{SCREENS}/{png}").convert("RGB")
    sw = round(shot.width * (height - 40) / shot.height)
    shot = shot.resize((sw, height - 40), Image.LANCZOS)
    body = Image.new("RGBA", (sw + 40, height), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle([0, 0, sw + 39, height - 1], radius=56, fill=(12, 26, 18))
    m = Image.new("L", shot.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, sw - 1, height - 41], radius=38, fill=255)
    body.paste(shot, (20, 20), m)
    return body

def wrap(d, text, fnt, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if d.textlength(t, font=fnt) <= width: cur = t
        else: lines.append(cur); cur = w
    if cur: lines.append(cur)
    return lines

TAG_W, TAG_H = 400, 92
TAG_X, TAG_Y = CAM_X - 24 - TAG_W, CAM_Y + CAM_D // 2 - TAG_H // 2

def cam_ring_and_tag(img, sp):
    d = ImageDraw.Draw(img)
    r = CAM_D // 2; cx, cy = CAM_X + r, CAM_Y + r
    d.ellipse([cx - r - 8, cy - r - 8, cx + r + 8, cy + r + 8], fill=MARIGOLD)
    d.ellipse([cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2], fill=PAPER)

def tag_png(sp, path):
    img = Image.new("RGBA", (TAG_W, TAG_H), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, TAG_W - 1, TAG_H - 1], radius=14, fill=(20, 67, 42, 255), outline=(242, 169, 0, 255), width=3)
    d.text((22, 8), sp["name"], font=font(800, 38), fill=PAPER)
    d.text((22, 54), f"{sp['role']}, Acxelin Labs", font=font(400, 24), fill=(205, 225, 210))
    img.save(path)

def stage_png(sp, png, title, desc, idx, n, path):
    img = Image.new("RGB", (W, H), CHALK); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 780, H], fill=(226, 236, 228))  # soft panel behind the phone
    ph = phone(png, 1000); img.paste(ph, ((780 - ph.width) // 2, 40), ph)
    d.text((RIGHT_X, 70), "HisaabSaathi", font=font(800, 44), fill=INK)
    d.text((RIGHT_X + 300, 84), "Voice ledger for daily-wage workers", font=font(400, 28), fill=SOFT)
    # progress dots: where we are in the demo
    for i in range(n):
        x = RIGHT_X + i * 30
        d.ellipse([x, 150, x + 14, 164], fill=INK if i <= idx else (197, 211, 200))
    d.text((RIGHT_X, 200), title, font=font(800, 66), fill=INK)
    y = 300
    for line in wrap(d, desc, font(400, 38), RIGHT_W - 40):
        d.text((RIGHT_X, y), line, font=font(400, 38), fill=(42, 72, 55)); y += 52
    cam_ring_and_tag(img, sp)
    img.save(path)

def closing_png(sp, path):
    img = Image.new("RGB", (W, H), INK); d = ImageDraw.Draw(img)
    d.text((W // 2, 150), "HisaabSaathi", font=font(800, 120), fill=PAPER, anchor="mm")
    d.text((W // 2, 255), "Every worker's own record, in their own language", font=font(600, 48), fill=MARIGOLD, anchor="mm")
    for i, t in enumerate(["Hindi, Odia, Telugu, Kannada, Tamil and English",
                           "All AI runs on the phone. Works offline. Free for workers.",
                           "Open model: huggingface.co/QubitMan/hisaabsaathi-model"]):
        d.text((W // 2, 350 + i * 56), t, font=font(400, 36), fill=(205, 225, 210), anchor="mm")
    d.text((W // 2, 545), "Acxelin Labs", font=font(800, 44), fill=PAPER, anchor="mm")
    cam_ring_and_tag(img, sp)
    img.save(path)

# ---------------------------------------------------------------- 1. speaker audio + square face clips
offset, timeline, events, cam_clips = 0.0, [], [], []
n_stages = sum(1 for sp in SPEAKERS for st in sp["stages"] if st[1])
idx = 0
for sp in SPEAKERS:
    rs = keep_ranges(sp); f = mapper(rs, offset); dur = sum(b - a for a, b in rs) / SPEED
    cx, cy, cs = sp["crop"]
    seglist = f"{OUT}/{sp['key']}_segs.txt"
    with open(seglist, "w") as o:
        for i, (a, b) in enumerate(rs):
            seg = f"{OUT}/{sp['key']}_seg{i}.mkv"
            run(["ffmpeg", "-v", "error", "-y", "-ss", f"{a:.3f}", "-i", os.path.join(D, sp["src"]), "-t", f"{b - a:.3f}",
                 "-vf", f"crop={cs}:{cs}:{cx}:{cy},scale=480:480,fps={FPS},format=yuv420p",
                 "-af", "aresample=48000,aformat=channel_layouts=stereo", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18",
                 "-c:a", "pcm_s16le", seg])
            o.write(f"file '{seg}'\n")
    clip = f"{OUT}/{sp['key']}_cam.mp4"
    run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", seglist,
         "-vf", f"setpts=PTS/{SPEED},fps={FPS}",
         "-af", f"atempo={SPEED},loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000,afade=in:st=0:d=0.08,afade=out:st={dur - 0.12:.3f}:d=0.12",
         "-t", f"{dur:.3f}", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-c:a", "pcm_s16le", "-f", "matroska", clip])
    cam_clips.append(clip)
    tp = f"{OUT}/tag_{sp['key']}.png"; tag_png(sp, tp); sp["span"] = (offset, offset + dur, tp)
    # stages on the global timeline
    for t, png, title, desc in sp["stages"]:
        p = f"{OUT}/stage_{len(timeline):02d}.png"
        if png: stage_png(sp, png, title, desc, idx, n_stages, p); idx += 1
        else: closing_png(sp, p)
        timeline.append([f(t), p])
    # subtitles on the global timeline
    groups = []
    for s, e, chunks in sp["segs"]:
        if groups and groups[-1][2] == chunks: groups[-1][1] = e
        else: groups.append([s, e, chunks])
    for s, e, chunks in groups:
        a, b = f(s), f(e); n = sum(len(c) for c in chunks); t = a
        for c in chunks:
            dd = (b - a) * len(c) / n; events.append((t, t + dd, c)); t += dd
    print(sp["key"], f"{offset:.1f}-{offset + dur:.1f}s")
    offset += dur
TOTAL = offset

# ---------------------------------------------------------------- 2. background: stages joined with crossfades
durs = [(timeline[i + 1][0] if i + 1 < len(timeline) else TOTAL) - timeline[i][0] for i in range(len(timeline))]
inputs, fc = [], []
for i, (t, p) in enumerate(timeline):
    length = durs[i] + (XF if i + 1 < len(timeline) else 0)
    inputs += ["-loop", "1", "-framerate", str(FPS), "-t", f"{length:.3f}", "-i", p]
prev, acc = "[0:v]", 0.0
for i in range(1, len(timeline)):
    acc += durs[i - 1]
    lab = f"[x{i}]"
    fc.append(f"{prev}[{i}:v]xfade=transition=fadewhite:duration={XF}:offset={acc - XF / 2:.3f}{lab}")
    prev = lab
bg = f"{OUT}/background.mp4"
run(["ffmpeg", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(fc) + f";{prev}format=yuv420p[v]", "-map", "[v]",
     "-r", str(FPS), "-c:v", "libx264", "-preset", "veryfast", "-crf", "16", "-t", f"{TOTAL:.3f}", bg])

# ---------------------------------------------------------------- 3. webcam track and audio
lst = f"{OUT}/cams.txt"
with open(lst, "w") as o: o.writelines(f"file '{c}'\n" for c in cam_clips)
cam = f"{OUT}/cam_all.mkv"
run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", cam])
mask = f"{OUT}/mask.png"
mk = Image.new("L", (CAM_D, CAM_D), 0); ImageDraw.Draw(mk).ellipse([0, 0, CAM_D - 1, CAM_D - 1], fill=255); mk.save(mask)

# ---------------------------------------------------------------- 4. subtitles (above the webcam, right column)
def ts(t):
    t = max(0.0, t); return f"{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}"
ass = f"{OUT}/subs.ass"
with open(ass, "w", encoding="utf-8") as o:
    o.write(f"""[Script Info]
ScriptType: v4.00+
PlayResX: {W}
PlayResY: {H}
WrapStyle: 0

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Cap,Anek Latin SemiBold,40,&H00FFFFFF,&H00FFFFFF,&H002A4314,&H002A4314,0,0,0,0,100,100,0,0,3,16,0,1,{RIGHT_X},{W - RIGHT_X - RIGHT_W + 40},{H - 740},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
""")
    for a, b, c in events: o.write(f"Dialogue: 0,{ts(a)},{ts(b)},Cap,,0,0,0,,{c}\n")

# ---------------------------------------------------------------- 5. compose
final = os.path.join(D, "HisaabSaathi-demo.mp4")
tag_inputs, tag_fc, prev = [], "", "[b0]"
for i, sp in enumerate(SPEAKERS):
    a, b, tp = sp["span"]
    tag_inputs += ["-loop", "1", "-i", tp]
    tag_fc += f";{prev}[{3 + i}:v]overlay={TAG_X}:{TAG_Y}:enable='between(t,{a:.3f},{b:.3f})'[b{i + 1}]"
    prev = f"[b{i + 1}]"
fc = (f"[1:v]scale={CAM_D}:{CAM_D},format=rgba[c];[2:v]format=gray,scale={CAM_D}:{CAM_D}[m];[c][m]alphamerge[cm];"
      f"[0:v][cm]overlay={CAM_X}:{CAM_Y}:shortest=1[b0]" + tag_fc +
      f";{prev}subtitles={ass}:fontsdir={FONTS},format=yuv420p[v]")
run(["ffmpeg", "-v", "error", "-y", "-i", bg, "-i", cam, "-loop", "1", "-i", mask, *tag_inputs, "-filter_complex", fc,
     "-map", "[v]", "-map", "1:a", "-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p",
     "-c:a", "aac", "-b:a", "192k", "-t", f"{TOTAL:.3f}", "-movflags", "+faststart", final])
print(final, f"{TOTAL:.1f}s")
