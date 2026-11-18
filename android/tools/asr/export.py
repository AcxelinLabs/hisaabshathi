"""Exports the (fine-tuned) Hindi CTC model to ONNX in the exact format the app's speech
engine (sherpa-onnx, NeMo CTC) expects: input mel features (B, 80, T) + lengths, output
log-probs over all 5633 tokens with every non-Hindi token masked out.

usage: export.py <ckpt_dir> <out.onnx> [finetuned.pt]
"""
import sys, os, torch, onnx
sys.path.insert(0, os.path.dirname(__file__))
from icmodel import ICModel

ckpt, out = sys.argv[1], sys.argv[2]
m = ICModel(ckpt, "hi").eval()
if len(sys.argv) > 3:
    m.load_state_dict(torch.load(sys.argv[3], map_location="cpu", weights_only=True), strict=False)

class Export(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.encoder, self.ctc = m.encoder, m.ctc
        mask = torch.full((m.blank + 1,), -1e4)
        mask[m.lo:m.hi] = 0; mask[m.blank] = 0
        self.register_buffer("mask", mask)

    def forward(self, audio_signal, length):
        enc, elen = self.encoder(audio_signal=audio_signal, length=length)
        logits = self.ctc.decoder_layers(enc).transpose(1, 2)
        return (logits + self.mask).log_softmax(-1)

e = Export(m).eval()
x = torch.randn(1, 80, 400); l = torch.tensor([400])
torch.onnx.export(e, (x, l), out, input_names=["audio_signal", "length"], output_names=["logprobs"],
                  dynamic_axes={"audio_signal": {0: "batch", 2: "time"}, "length": {0: "batch"}, "logprobs": {0: "batch", 1: "frames"}},
                  opset_version=17, do_constant_folding=True)
mo = onnx.load(out)
meta = {"vocab_size": str(m.blank + 1), "normalize_type": "per_feature", "subsampling_factor": "4",
        "model_type": "EncDecCTCModelBPE", "version": "1", "model_author": "ai4bharat",
        "comment": "ai4bharat/indicconformer_stt_hi_hybrid_ctc_rnnt_large, CTC-only, Hindi-masked"
                   + (", fine-tuned by Acxelin Labs on rural Hindi speech" if len(sys.argv) > 3 else "")}
del mo.metadata_props[:]
for k, v in meta.items():
    p = mo.metadata_props.add(); p.key, p.value = k, v
onnx.save(mo, out)
print("wrote", out, round(os.path.getsize(out) / 1e6), "MB")
