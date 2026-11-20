# Set up on Azure (Central India)

Everything about workers' data runs and stays in Azure, in India. Hugging Face only hosts
public files: the app's model download and the APK.

## The Azure services

### Needed now (upload, nightly pipeline, versioned datasets)

| Service | What it does for HisaabSaathi | Approx. per month |
|---|---|---|
| **Resource group** | One folder holding everything below, so it can be managed and paid for together | Free |
| **Microsoft Entra ID**: managed identities and roles | Each part signs in as itself, with no passwords, and can do only its own job | Free |
| **Storage account** (Blob Storage) | `improve`: sealed uploads from phones. `processed`: the pipeline's data, snapshots, audit | $1–5 |
| **Key Vault** | Holds the team's private key and the service secret; every access is logged | under $1 |
| **Container Apps environment** | Runs the two programs below; scales to zero when idle | (in the next two) |
| ↳ **Container App**: upload service | The address phones talk to | $0–35 |
| ↳ **Container Apps Job**: nightly pipeline | Every night at 02:00: deletions, opening, screening, snapshot, report | ~$0 (free allowance) |
| **Container Registry** (Basic) | Stores the program image both of them run | ~$5 |
| **Log Analytics** (Azure Monitor) | Logs and each night's summary; never IP addresses or workers' words | $0–3 |
| **Azure Machine Learning workspace** | Each snapshot becomes a versioned dataset (`hisaab-train`, `hisaab-eval`); later, training and models | A few $ for its own storage, Key Vault and Application Insights |
| **Cost Management** budget | An email if spending passes your limit | Free |

### Needed later (agent and training)

| Service | What it does | Approx. |
|---|---|---|
| **Azure AI Foundry**: project and a model deployment | The agent that reads the failure report and proposes fixes and labels | $10–60 a month, weekly runs |
| **Azure ML compute cluster (GPU)** and GPU quota | Fine-tuning the language model; later the speech models | $5–10 per monthly language-model run; $20–100 per speech run |
| A second **Container Apps Job** | Runs the app's tests, gates and leak check on the agent's changes | ~$0–2 |

### Optional, when usage grows

| Service | Why |
|---|---|
| **Microsoft Defender for Cloud** | Free security recommendations; paid plans add threat detection for storage and containers |
| **Private endpoints and a virtual network** | Take storage and Key Vault off the public internet entirely |
| **Azure Front Door with WAF** | Blocks floods and bots before they reach the upload service |

**Not needed:** Azure Files, SQL or Cosmos DB, Event Grid, Functions, AKS, Data Factory.

**Outside Azure:**
- **GitHub:** code, pull requests and release builds.
- **Hugging Face:** public model and APK downloads only.

A typical total at pilot scale (about 1,000 workers sharing) is about **$25–120 a month**,
most of it the agent and training. These prices change, so check them in the
[Azure Pricing Calculator](https://azure.microsoft.com/pricing/calculator/) for Central India.

---

## Step by step

PowerShell, run from your `hisaabshathi` folder. This takes about 45 minutes the first time.
Run it all in one PowerShell window, because later steps use names from earlier ones.

### 1. Tools and login (once)

```powershell
winget install -e --id Microsoft.AzureCLI          # then close and reopen PowerShell
az login
az extension add --name containerapp --upgrade
az extension add --name ml --upgrade
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights
az provider register --namespace Microsoft.MachineLearningServices
```

### 2. Names

```powershell
$RG = "hisaab-improve"; $LOC = "centralindia"; $APP = "hisaab-improve"; $JOB = "hisaab-nightly"; $WS = "hisaab-ml"
$N = Get-Random -Maximum 99999; $SA = "hisaabimprove$N"; $KV = "hisaab-kv-$N"; "$SA  $KV"   # write these down
$SUB = az account show --query id -o tsv
$ME = az ad signed-in-user show --query id -o tsv
```

### 3. The team key (on your computer, once)

```powershell
cd server
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python -m hisaab_improve.crypto new-key $HOME\hisaab-team-key.pem
cd ..
```

It prints the **public key**, which goes into the app build and isn't secret. It writes the
private key to `hisaab-team-key.pem`.

**Keep a backup of that file offline**, such as in a password manager. Without it, nothing
phones sent can be opened. It goes into Key Vault in step 5.

### 4. Resource group, storage, budget

```powershell
az group create --name $RG --location $LOC
az storage account create --name $SA --resource-group $RG --location $LOC --sku Standard_LRS --kind StorageV2 `
  --min-tls-version TLS1_2 --allow-blob-public-access false --allow-shared-key-access false
az storage container-rm create --storage-account $SA --resource-group $RG --name improve
az storage container-rm create --storage-account $SA --resource-group $RG --name processed
$SAID = az storage account show --name $SA --resource-group $RG --query id -o tsv
```

- Leave blob versioning and soft delete **off**. With them on, "Delete what I shared" would
  only hide files.
- **Budget:** in the Azure portal, open Cost Management, then Budgets, then Add. Set $50 a
  month, with an email at 80%.

### 5. Key Vault: the team key and the service secret

```powershell
az keyvault create --name $KV --resource-group $RG --location $LOC --enable-rbac-authorization true
$KVID = az keyvault show --name $KV --query id -o tsv
az role assignment create --assignee $ME --role "Key Vault Secrets Officer" --scope $KVID
# (wait a minute for the role to start working)
az keyvault secret set --vault-name $KV --name team-key --file "$HOME\hisaab-team-key.pem"
az keyvault secret set --vault-name $KV --name service-secret --value (python -c "import secrets; print(secrets.token_urlsafe(48))")
```

Once the key is in Key Vault and backed up, delete `hisaab-team-key.pem` from your computer.

### 6. The upload service

```powershell
az containerapp up --name $APP --resource-group $RG --location $LOC --source .\server --ingress external --target-port 8080 `
  --env-vars HISAAB_STORAGE=azure HISAAB_AZURE_CONTAINER=improve "HISAAB_AZURE_ACCOUNT_URL=https://$SA.blob.core.windows.net"
```

This builds the image in Azure, which takes a few minutes. It will say the app failed to
start. That's expected: the service refuses to run without its secret, which you add next.

```powershell
az containerapp identity assign --name $APP --resource-group $RG --system-assigned
$MI = az containerapp identity show --name $APP --resource-group $RG --query principalId -o tsv
# Write only to `improve`, make short upload links, read only the service secret. No access to `processed`.
az role assignment create --assignee-object-id $MI --assignee-principal-type ServicePrincipal --role "Storage Blob Data Contributor" --scope "$SAID/blobServices/default/containers/improve"
az role assignment create --assignee-object-id $MI --assignee-principal-type ServicePrincipal --role "Storage Blob Delegator" --scope $SAID
az role assignment create --assignee-object-id $MI --assignee-principal-type ServicePrincipal --role "Key Vault Secrets User" --scope "$KVID/secrets/service-secret"
# (wait a minute)
az containerapp secret set --name $APP --resource-group $RG --secrets "hisaab-secret=keyvaultref:https://$KV.vault.azure.net/secrets/service-secret,identityref:system"
az containerapp update --name $APP --resource-group $RG --set-env-vars "HISAAB_SECRET=secretref:hisaab-secret" --min-replicas 0 --max-replicas 2
$URL = "https://" + (az containerapp show --name $APP --resource-group $RG --query properties.configuration.ingress.fqdn -o tsv); $URL
curl.exe "$URL/healthz"    # {"ok":true,...}
```

### 7. Azure Machine Learning: versioned datasets

```powershell
az ml workspace create --name $WS --resource-group $RG --location $LOC
@"
`$schema: https://azuremlschemas.azureedge.net/latest/azureBlob.schema.json
name: hisaab_processed
type: azure_blob
account_name: $SA
container_name: processed
"@ | Set-Content datastore.yml
az ml datastore create --file datastore.yml --resource-group $RG --workspace-name $WS
Remove-Item datastore.yml
$WSID = az ml workspace show --name $WS --resource-group $RG --query id -o tsv
```

The datastore holds no password: whoever uses it signs in as themselves.

### 8. The nightly job

```powershell
$ENVID = az containerapp show --name $APP --resource-group $RG --query properties.environmentId -o tsv
$IMAGE = az containerapp show --name $APP --resource-group $RG --query "properties.template.containers[0].image" -o tsv
$ACR = $IMAGE.Split("/")[0]

az containerapp job create --name $JOB --resource-group $RG --environment $ENVID `
  --trigger-type Schedule --cron-expression "30 20 * * *" `
  --replica-timeout 7200 --replica-retry-limit 1 --parallelism 1 --replica-completion-count 1 `
  --image $IMAGE --cpu 1.0 --memory 2.0Gi --command "python" --args "-m" "hisaab_improve.pipeline" "nightly" `
  --mi-system-assigned --registry-server $ACR --registry-identity system `
  --env-vars HISAAB_STORAGE=azure "HISAAB_AZURE_ACCOUNT_URL=https://$SA.blob.core.windows.net" HISAAB_AZURE_CONTAINER=improve `
             HISAAB_WORK_CONTAINER=processed HISAAB_RAW_DAYS=30 `
             "HISAAB_AML_SUBSCRIPTION=$SUB" "HISAAB_AML_RESOURCE_GROUP=$RG" "HISAAB_AML_WORKSPACE=$WS" HISAAB_AML_DATASTORE=hisaab_processed

$JMI = az containerapp job show --name $JOB --resource-group $RG --query identity.principalId -o tsv
# Both containers, the team key only, and registering datasets.
az role assignment create --assignee-object-id $JMI --assignee-principal-type ServicePrincipal --role "Storage Blob Data Contributor" --scope $SAID
az role assignment create --assignee-object-id $JMI --assignee-principal-type ServicePrincipal --role "Key Vault Secrets User" --scope "$KVID/secrets/team-key"
az role assignment create --assignee-object-id $JMI --assignee-principal-type ServicePrincipal --role "AzureML Data Scientist" --scope $WSID
# (wait a minute)
az containerapp job secret set --name $JOB --resource-group $RG --secrets "team-key=keyvaultref:https://$KV.vault.azure.net/secrets/team-key,identityref:system"
az containerapp job update --name $JOB --resource-group $RG --set-env-vars HISAAB_PRIVATE_KEYS_B64=secretref:team-key
```

`30 20 * * *` is 20:30 UTC, which is 02:00 in India. To run it now and check it:

```powershell
az containerapp job start --name $JOB --resource-group $RG
az containerapp job execution list --name $JOB --resource-group $RG -o table
```

- **Each run's summary** is in the job's logs: in the portal, open the job, then Execution
  history, then Logs.
- **A full record of each run** is kept in `processed/audit/`.
- **The datasets** appear in Azure ML studio under Data, as `hisaab-train` and `hisaab-eval`,
  one version per night.

**Optional: a nightly message to the team.** It holds counts only. It works with any webhook
that accepts `{"text": "..."}`, such as Slack, or a Teams workflow that posts the `text` field:

```powershell
az keyvault secret set --vault-name $KV --name webhook --value "<webhook address>"
az role assignment create --assignee-object-id $JMI --assignee-principal-type ServicePrincipal --role "Key Vault Secrets User" --scope "$KVID/secrets/webhook"
az containerapp job secret set --name $JOB --resource-group $RG --secrets "webhook=keyvaultref:https://$KV.vault.azure.net/secrets/webhook,identityref:system"
az containerapp job update --name $JOB --resource-group $RG --set-env-vars HISAAB_NOTIFY_WEBHOOK=secretref:webhook
```

### 9. Build the app that sends

Give the service address (`$URL`) and the **public** key from step 3 to whoever builds the
app. Neither is secret. Or build it yourself:

```powershell
cd android
.\gradlew :app:assembleRelease "-Phisaab.shareUrl=$URL" "-Phisaab.shareKey=<public key>"
```

A build without both still asks workers, but sends nothing.

## Checking labels and held-back phones (from your computer)

Give yourself access once:

```powershell
az role assignment create --assignee $ME --role "Storage Blob Data Contributor" --scope $SAID
```

Then, in `server` with the virtual environment from step 3:

```powershell
$env:HISAAB_STORAGE="azure"; $env:HISAAB_AZURE_ACCOUNT_URL="https://$SA.blob.core.windows.net"
$env:HISAAB_AZURE_CONTAINER="improve"; $env:HISAAB_WORK_CONTAINER="processed"
.venv\Scripts\python -m hisaab_improve.pipeline review-sheet review.csv
#   open review.csv in Excel: "yes" under accept, or a better label under correct_label; save as CSV UTF-8
.venv\Scripts\python -m hisaab_improve.pipeline review-apply review.csv --reviewer "Your name"
.venv\Scripts\python -m hisaab_improve.pipeline clear <install id from the report> --reviewer "Your name"
```

## Updating after code changes

```powershell
az containerapp up --name $APP --resource-group $RG --source .\server
$IMAGE = az containerapp show --name $APP --resource-group $RG --query "properties.template.containers[0].image" -o tsv
az containerapp job update --name $JOB --resource-group $RG --image $IMAGE
```

## Changing keys

- **The service secret:** add a new version of `service-secret` in Key Vault, and put the old
  value in `HISAAB_SECRET_PREVIOUS` for a few weeks. Phones keep working throughout.
- **The team key:** make a new one (step 3) and build the app with the new public key. Until
  no phone uses the old key, store both in `team-key`, in one secret, the two PEM texts
  separated by a comma.

## Who can see workers' data

| Identity | `improve` (sealed uploads) | `processed` (opened data) | Team key |
|---|---|---|---|
| Upload service | write | none | none |
| Nightly job | read, delete | read, write | read |
| People on the team you give a role | as you assign | as you assign | Key Vault Secrets Officer only |
