# Scan Vaani metadata (no audio) for transcribed clips; record shard + row group + row index.
import pyarrow.parquet as pq, json, random
from huggingface_hub import HfFileSystem, HfApi
from concurrent.futures import ThreadPoolExecutor
fs=HfFileSystem(); sib=HfApi().dataset_info("ARTPARK-IISc/Vaani").siblings
COLS=["language","duration","speakerID","gender","state","district","pincode","isTranscriptionAvailable","transcript","UtteranceSequenceID","speakerImageHash"]
def scan(f):
    for a in range(5):
        try:
            pf=pq.ParquetFile(fs.open("datasets/ARTPARK-IISc/Vaani/"+f, block_size=1<<20)); out=[]
            for g in range(pf.num_row_groups):
                for i,r in enumerate(pf.read_row_group(g,columns=COLS).to_pylist()):
                    if r["isTranscriptionAvailable"]=="Yes" and r["transcript"].strip(): out.append({**r,"file":f,"rg":g,"row":i})
            return out
        except Exception as e: err=e
    print("ERR",f,str(err)[:100],flush=True); return []
res={}
for L in ["Odia","Telugu","Kannada","Tamil"]:
    files=sorted(s.rfilename for s in sib if s.rfilename.startswith(f"audio/{L}/"))
    random.Random(0).shuffle(files); files=files[:60]   # spread across districts, bounded
    rows=[]
    with ThreadPoolExecutor(8) as ex:
        for o in ex.map(scan, files): rows+=o
    res[L]=rows
    print(L, len(files),"shards scanned of", len(sib and [1]), "transcribed:",len(rows), "hours:", round(sum(r["duration"] for r in rows)/3600,2),
          "districts:", len({r["district"] for r in rows}), flush=True)
json.dump(res, open("vaani_meta.json","w"), ensure_ascii=False)
