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

## Odia, Telugu, Kannada, Tamil test (`indic/`, 18 Nov 2026)

`indic/run.sh` rebuilds the test sets and scores the app's IndicConformer models in
sherpa-onnx 1.13.8 (results in `android/README.md`). Steps: `download.py` (IndicVoices
`valid`), `ivspk.py` (IndicVoices train speaker IDs, column-only reads), `vmeta.py` (Vaani
metadata, no audio), `build_iv.py` / `build_ivseen.py` / `build_vaani.py` (16 kHz WAVs and
manifests; Vaani audio is fetched only for the row groups that hold chosen clips),
`decode.py` (the app's recogniser settings), then `PipelineEvalTest` (the app's own
`NumberWords` and `Understand` on reference and recogniser text) and `score.py`.

`indic/sets/` holds every test clip's reference, recogniser output and app result;
`indic/sets/test_speakers.json` lists the test speakers, to keep out of fine-tuning.
Audio is not stored; `run.sh` fetches it again. The Vaani subset with official splits
(`ARTPARK-IISc/Vaani-transcription-part`) needs separate access approval, so the main
`ARTPARK-IISc/Vaani` dataset's transcribed clips are used.
