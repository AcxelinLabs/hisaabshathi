#!/usr/bin/env bash
# Indian-language speech test (Odia, Telugu, Kannada, Tamil) in the app's engine, sherpa-onnx 1.13.8.
# ASR_DIR: working folder with the s138 venv (sherpa-onnx==1.13.8 soundfile numpy pyarrow huggingface_hub)
# and models/{tokens.txt,or,te,kn,ta/model.int8.onnx} from parismitaglobalsolutions/indicconformer-sherpa-onnx.
set -euo pipefail
T=$(cd "$(dirname "$0")" && pwd); A=$(cd "$T/../../.." && pwd)
cd "${ASR_DIR:?set ASR_DIR}"; P="s138/bin/python -I"
$P "$T/download.py"; $P "$T/ivspk.py"; $P "$T/vmeta.py"
$P "$T/build_iv.py"; $P "$T/build_ivseen.py"; $P "$T/build_vaani.py"
for p in "odia or" "telugu te" "kannada kn" "tamil ta"; do set -- $p
  for s in iv ivseen va; do $P "$T/decode.py" sets/${s}_$1.jsonl models/$2/model.int8.onnx models/tokens.txt sets/${s}_$1.hyp.jsonl; done; done
cat sets/*.hyp.jsonl | $P -c 'import json,sys
for l in sys.stdin: r=json.loads(l); print(json.dumps({"id":r["id"],"ref":r["text"],"hyp":r["hyp"]},ensure_ascii=False))' > pipe.jsonl
(cd "$A" && PIPELINE_IN="$ASR_DIR/pipe.jsonl" PIPELINE_OUT="$ASR_DIR/pipe.out" ./gradlew -q :core:test --tests '*PipelineEvalTest*' --rerun)
$P "$T/score.py" pipe.out sets/va_*.hyp.jsonl sets/iv_*.hyp.jsonl sets/ivseen_*.hyp.jsonl | tee results.jsonl
