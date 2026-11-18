# All comparisons in the app's own engine version (sherpa-onnx 1.13.8).
cd "${ASR_DIR:?set ASR_DIR to the working folder with splits/, onnx/, en/ and the s138 venv}"
M=../modelcheck/models/stt
for s in fleurs rw rj sruti karya_hi; do
  for v in "app:$M/hi/model.int8.onnx" "base-fp32:onnx/base-fp32.onnx" "ft-q8:onnx/ft-q8.onnx" "ft-fp32:onnx/ft-fp32.onnx"; do
    name=${v%%:*}; f=${v#*:}; [ -f "$f" ] || continue
    s138/bin/python -I scripts/sherpa_eval.py "$f" $M/tokens.txt splits/test_$s.jsonl 2>/dev/null | python3 -c "import json,sys;d=json.loads(sys.stdin.read());d['model']='$name';print(json.dumps(d))" >> results.jsonl
  done
done
P="s138/bin/python -I scripts/en_eval.py"
$P current-fastconformer-ctc ctc $M/en 4; $P parakeet-ctc-110m-int8 ctc en/pctc8 4; $P moonshine-base-int8 moonshine en/moon 4; $P parakeet-tdt-0.6b-v2-int8 tdt en/p06 4
echo ALLDONE
