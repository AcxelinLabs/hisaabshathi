"""Registers each snapshot as versioned Azure Machine Learning data assets, so every training
run names exactly the data it used, and Azure ML records which model came from which version.

    hisaab-train  version <snapshot id>  ->  processed/snapshots/<id>/train/
    hisaab-eval   version <snapshot id>  ->  processed/snapshots/<id>/eval/

The assets are references, not copies: the data stays in the `processed` container. When a
snapshot is retired (a phone in it asked for deletion) or ages out, its versions are archived,
and its files are already gone.

It uses Azure ML's REST API with the job's managed identity, which needs the
"AzureML Data Scientist" role on the workspace.
"""
from __future__ import annotations

import json
import os
import urllib.request

from .common import read_json, write_json

API = "2024-04-01"
ASSETS = ("train", "eval")


class AzureML:
    def __init__(self, subscription: str, resource_group: str, workspace: str, datastore: str, request=None):
        self.sub, self.rg, self.ws, self.datastore = subscription, resource_group, workspace, datastore
        self._request = request or self._http

    @classmethod
    def from_env(cls) -> "AzureML | None":
        e = os.environ.get
        names = [e("HISAAB_AML_SUBSCRIPTION"), e("HISAAB_AML_RESOURCE_GROUP"), e("HISAAB_AML_WORKSPACE")]
        return cls(*names, e("HISAAB_AML_DATASTORE", "hisaab_processed")) if all(names) else None

    def _http(self, method: str, url: str, body: dict) -> int:
        from azure.identity import DefaultAzureCredential

        token = DefaultAzureCredential().get_token("https://management.azure.com/.default").token
        req = urllib.request.Request(url, data=json.dumps(body).encode(), method=method,
                                     headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status

    def _url(self, name: str, version: str) -> str:
        return (f"https://management.azure.com/subscriptions/{self.sub}/resourceGroups/{self.rg}/providers/"
                f"Microsoft.MachineLearningServices/workspaces/{self.ws}/data/{name}/versions/{version}?api-version={API}")

    def _body(self, sid: str, split: str, archived: bool) -> dict:
        uri = (f"azureml://subscriptions/{self.sub}/resourcegroups/{self.rg}/workspaces/{self.ws}"
               f"/datastores/{self.datastore}/paths/snapshots/{sid}/{split}/")
        return {"properties": {
            "dataType": "uri_folder", "dataUri": uri, "isArchived": archived,
            "description": f"HisaabSaathi snapshot {sid}, {split} split ({'retired' if archived else 'current'})",
            "tags": {"snapshot": sid, "split": split},
        }}

    def register(self, work, sid: str) -> list[str]:
        done = read_json(work, "state/aml.json", {})
        for split in ASSETS:
            self._request("PUT", self._url(f"hisaab-{split}", sid), self._body(sid, split, False))
        done[sid] = True
        write_json(work, "state/aml.json", done)
        return [f"hisaab-{s}:{sid}" for s in ASSETS]

    def archive_gone(self, work, existing: set[str]) -> list[str]:
        """Archives the versions of snapshots that no longer exist."""
        done = read_json(work, "state/aml.json", {})
        gone = [sid for sid in done if sid not in existing]
        for sid in gone:
            for split in ASSETS:
                self._request("PUT", self._url(f"hisaab-{split}", sid), self._body(sid, split, True))
            del done[sid]
        write_json(work, "state/aml.json", done)
        return gone
