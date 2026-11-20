import json

from hisaab_improve.gates import compare, main as gates_main
from hisaab_improve.leakcheck import check, worker_texts


def test_no_language_may_get_worse():
    base = {"target_right_rate": {"hi": 0.70, "or": 0.60}, "slm_cer": {"hi": 0.10}, "gate_right_rate": {"hi": 1.0}}
    better = {"target_right_rate": {"hi": 0.75, "or": 0.60}, "slm_cer": {"hi": 0.08}, "gate_right_rate": {"hi": 1.0}}
    assert compare(base, better) == []
    worse = {"target_right_rate": {"hi": 0.80, "or": 0.55}, "slm_cer": {"hi": 0.12}, "gate_right_rate": {"hi": 0.99}}
    problems = compare(base, worse)
    assert any("or: worse" in p for p in problems) and any("slm_cer hi" in p for p in problems)
    assert any("gate_right_rate hi" in p for p in problems)
    assert compare(base, {"target_right_rate": {"hi": 0.9}})  # Odia and others missing
    assert compare({"target_right_rate": {"hi": 0.70}}, {"target_right_rate": {"hi": 0.698}}, tolerance=0.005) == []


def test_gates_command_exit_codes(tmp_path):
    b, c = tmp_path / "b.json", tmp_path / "c.json"
    b.write_text(json.dumps({"target_right_rate": {"hi": 0.7}}))
    c.write_text(json.dumps({"target_right_rate": {"hi": 0.6}}))
    assert gates_main([str(b), str(c)]) == 1
    assert gates_main([str(b), str(b)]) == 0


DIFF = """diff --git a/android/core/src/test/kotlin/com/acxelin/hisaabsaathi/core/QuestionRulesTest.kt b/android/core/src/test/kotlin/com/acxelin/hisaabsaathi/core/QuestionRulesTest.kt
--- a/android/core/src/test/kotlin/com/acxelin/hisaabsaathi/core/QuestionRulesTest.kt
+++ b/android/core/src/test/kotlin/com/acxelin/hisaabsaathi/core/QuestionRulesTest.kt
@@ -1,3 +1,4 @@
+        ask("{line}", "ASK DAY DUE")
"""


def snapshot(tmp_path, text):
    d = tmp_path / "snap" / "agent"
    d.mkdir(parents=True)
    (d / "to_label.jsonl").write_text(json.dumps({"id": "00000000000000aa", "lang": "hi", "text": text}, ensure_ascii=False) + "\n", encoding="utf-8")
    return worker_texts([tmp_path / "snap"])


def test_worker_words_are_caught_without_being_repeated(tmp_path):
    texts = snapshot(tmp_path, "सेठ जी पगार कब तक मिलेगा बताओ ना भाई")
    # The same sentence, punctuation changed: caught.
    f = check(DIFF.replace("{line}", "सेठ जी, पगार कब तक मिलेगा? बताओ ना भाई"), texts)
    assert f == ["worker words in android/core/src/test/kotlin/com/acxelin/hisaabsaathi/core/QuestionRulesTest.kt (sample 00000000000000aa)"]
    assert "पगार" not in f[0]
    # Five words in a row kept from it: caught.
    assert check(DIFF.replace("{line}", "मालिक पगार कब तक मिलेगा बताओ"), texts)
    # A real paraphrase: fine.
    assert check(DIFF.replace("{line}", "मेरी मजदूरी किस दिन आएगी"), texts) == []


def test_collection_code_and_pipeline_are_off_limits(tmp_path):
    texts = snapshot(tmp_path, "x")
    for path in ("android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/Improve.kt", "server/hisaab_improve/app.py",
                 "android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/i18n/Te.kt"):
        d = f"diff --git a/{path} b/{path}\n--- a/{path}\n+++ b/{path}\n+changed\n"
        assert check(d, texts) == [f"protected file changed: {path}"]
    ok = "diff --git a/android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/QuestionWords.kt b/android/core/src/main/kotlin/com/acxelin/hisaabsaathi/core/QuestionWords.kt\n+\"dadan\"\n"
    assert check(ok, texts) == []
