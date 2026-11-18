# Speech fine-tuning and testing (Hindi IndicConformer, English candidates)

Tools from the Nov 2026 rural-Hindi fine-tune and the speech re-test (results in
`android/README.md`). They expect a working folder (`ASR_DIR`) with:

- `nemo/`: Python 3.11 venv with torch 2.5.1 (CPU), nemo_toolkit 2.1.0, onnx, onnxruntime, pyarrow, soundfile, num2words.
- `s138/`: venv with **sherpa-onnx 1.13.8**, the app's engine version. Score with this one:
  sherpa-onnx 1.12.x has a different audio frontend and gives wrong numbers for these models.
- `dl/hi/`: AI4Bharat `indicconformer_stt_hi_hybrid_ctc_rnnt_large` checkpoint, extracted
  (`model_config.yaml`, `model_weights.ckpt`, tokenizer). Weights load with `torch.load(weights_only=True)`.
- `data/`, `splits/`: 16 kHz WAVs and JSONL manifests made by `prep.py` (one `{"audio","text","speaker"}` per line).

| Script | What it does |
|---|---|
| `prep.py` | Hugging Face parquet shard to 16 kHz WAVs plus manifest |
| `icmodel.py` | Builds the Hindi CTC model from the checkpoint without NeMo's restore code |
| `train2.py` | Fine-tunes the upper conformer layers plus the CTC head on CPU (frozen lower layers cached) |
| `export.py` | Exports to the app's ONNX format (CTC, Hindi-masked); then `quantize_dynamic` (MatMul, QInt8) |
| `sherpa_eval.py`, `evalset.py`, `eval_all.py` | Word error rate, character error rate and spoken-number recall |
| `en_eval.py` | English models on Svarah, Karya and FLEURS (num2words normalisation) |
| `english_in_indic.py` | How each Indian-language model writes English number words (fed `core/NumberWords.kt`) |
| `onset.py` | Checks whether digital silence before speech drops the first word |
| `retest.sh` | Every Hindi and English comparison in the app's engine version |

Data used: AI4Bharat Rural_Women_ASR_v2 (CC-BY-4.0), Rural_Women_Bhojpuri / SRUTI
(CC-BY-SA-4.0), 1rsh/tts-rj-hi-karya (MIT), Google FLEURS, Svarah. Test speakers for Rural
Women are not in training; sentences shared between Karya train and test were dropped.
