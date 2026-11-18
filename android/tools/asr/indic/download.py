"""Downloads IndicVoices 'valid' parquet for Odia, Telugu, Kannada, Tamil into raw/ (needs an
HF token that has accepted the dataset terms)."""
import os
from huggingface_hub import HfApi, hf_hub_download
for s in HfApi().dataset_info("ai4bharat/IndicVoices").siblings:
    if s.rfilename.split("/")[0] in ("odia", "telugu", "kannada", "tamil") and "/valid-" in s.rfilename:
        print(hf_hub_download("ai4bharat/IndicVoices", s.rfilename, repo_type="dataset", local_dir="raw/IndicVoices"))
