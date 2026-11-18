import pyarrow.parquet as pq, json, sys
from huggingface_hub import HfFileSystem, HfApi
from concurrent.futures import ThreadPoolExecutor
fs=HfFileSystem(); sib=HfApi().dataset_info("ai4bharat/IndicVoices").siblings
def spk(f):
    for a in range(5):
        try: return f, sorted(set(pq.read_table(fs.open("datasets/ai4bharat/IndicVoices/"+f, block_size=1<<20), columns=["speaker_id"]).column(0).to_pylist()))
        except Exception as e: err=e
    return f, "ERR "+str(err)[:100]
out={}
for l in ["odia","telugu","kannada","tamil"]:
    files=[s.rfilename for s in sib if s.rfilename.startswith(f"{l}/train-")]
    with ThreadPoolExecutor(8) as ex:
        for f,s in ex.map(spk, files): out[f]=s
    print(l, len(files), "errors", sum(isinstance(out[f],str) for f in files), flush=True)
json.dump(out, open("iv_train_speakers.json","w"))
