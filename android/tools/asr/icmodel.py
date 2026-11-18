"""IndicConformer (per-language hybrid checkpoint) rebuilt as a plain PyTorch CTC model.

Weights are read with torch.load(weights_only=True): no code inside the checkpoint runs.
Only the CTC branch is kept (the app decodes with CTC), masked to one language's 256 tokens.
"""
import glob, os, yaml, torch, sentencepiece as spm
from nemo.collections.asr.modules import AudioToMelSpectrogramPreprocessor, ConformerEncoder, ConvASRDecoder

LANGS = ['as', 'bn', 'brx', 'doi', 'kok', 'gu', 'hi', 'kn', 'ks', 'mai', 'ml', 'mr', 'mni', 'ne', 'or', 'pa', 'sa', 'sat', 'sd', 'ta', 'te', 'ur']

def _clean(d):
    return {k: v for k, v in d.items() if k not in ('_target_', 'multisoftmax')}

class ICModel(torch.nn.Module):
    def __init__(self, ckpt_dir, lang):
        super().__init__()
        c = yaml.safe_load(open(f"{ckpt_dir}/model_config.yaml"))
        assert list(c['tokenizer']['langs']) == LANGS
        self.pre = AudioToMelSpectrogramPreprocessor(**_clean(c['preprocessor']))
        self.encoder = ConformerEncoder(**_clean(c['encoder']))
        d = c['aux_ctc']['decoder']
        self.ctc = ConvASRDecoder(feat_in=d['feat_in'], num_classes=d['num_classes'], vocabulary=d.get('vocabulary'))
        sd = torch.load(f"{ckpt_dir}/model_weights.ckpt", map_location="cpu", weights_only=True)
        mine = {}
        for k, v in sd.items():
            if k.startswith('preprocessor.'): mine['pre.' + k[13:]] = v
            elif k.startswith('encoder.'): mine[k] = v
            elif k.startswith('ctc_decoder.'): mine['ctc.' + k[12:]] = v
        missing, unexpected = self.load_state_dict(mine, strict=False)
        assert not unexpected, unexpected
        assert all('featurizer' in m or 'window' in m or 'fb' in m for m in missing), missing
        self.lang = lang
        i = LANGS.index(lang)
        self.lo, self.hi = i * 256, (i + 1) * 256           # this language's tokens
        self.blank = d['num_classes']                       # 5632
        tok = c['tokenizer']['langs'][lang]['model_path'].replace('nemo:', '')
        self.sp = spm.SentencePieceProcessor(model_file=f"{ckpt_dir}/{tok}")
        assert self.sp.get_piece_size() == 256

    def features(self, wav, lens):
        return self.pre(input_signal=wav, length=lens)

    def forward(self, feats, flens):
        enc, elens = self.encoder(audio_signal=feats, length=flens)
        logits = self.ctc.decoder_layers(enc).transpose(1, 2)   # B,T,5633 raw logits
        # Keep only this language's tokens + blank (what the app's model does).
        sub = torch.cat([logits[..., self.lo:self.hi], logits[..., self.blank:self.blank + 1]], -1)
        return sub.log_softmax(-1), elens                    # 257 classes, blank = 256

    def encode_text(self, text):
        return self.sp.encode(text)

    def decode_ids(self, ids):
        out, prev = [], None
        for t in ids:
            if t != prev and t != 256: out.append(t)
            prev = t
        return self.sp.decode(out)
