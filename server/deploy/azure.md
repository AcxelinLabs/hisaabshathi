# Set up on Azure (Central India)

PowerShell, run from your `hisaabshathi` folder. This takes about 30 minutes the first time.
At the end you have:

- **the upload service**: a Container App that phones talk to;
- **two private storage containers**: `improve`, where phones' sealed uploads land, and
  `processed`, where only the pipeline reads and writes;
- **the nightly job**: a Container Apps Job that runs the pipeline every night at 02:00 India time;
- **the team key**: phones seal every upload to it, and only the nightly job can open them.

The service can write only to `improve` and can't open what phones send. Only the nightly
job holds the private key and can read `processed`.

## 1. Tools and login (once)

```powershell
winget install -e --id Microsoft.AzureCLI          # then close and reopen PowerShell
az login
az extension add --name containerapp --upgrade
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights
```

## 2. Names

```powershell
$RG = "hisaab-improve"; $LOC = "centralindia"; $APP = "hisaab-improve"; $JOB = "hisaab-nightly"
$SA = "hisaabimprove" + (Get-Random -Maximum 99999); $SA   # write this down: you need it again later
```

## 3. The team key (on your computer, once)

```powershell
cd server
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python -m hisaab_improve.crypto new-key $HOME\hisaab-team-key.pem
cd ..
```

It prints the **public key**, for the app build; it isn't secret. It writes the **private
key** to `hisaab-team-key.pem`.

**Keep a backup of that file somewhere safe and offline**, such as a password manager.
Without it, nothing phones sent can be opened. Never put it in the repository, a chat or an
email.

## 4. Private storage

```powershell
az group create --name $RG --location $LOC
az storage account create --name $SA --resource-group $RG --location $LOC --sku Standard_LRS --kind StorageV2 `
  --min-tls-version TLS1_2 --allow-blob-public-access false --allow-shared-key-access false
az storage container-rm create --storage-account $SA --resource-group $RG --name improve
az storage container-rm create --storage-account $SA --resource-group $RG --name processed
$SAID = az storage account show --name $SA --resource-group $RG --query id -o tsv
```

Leave blob versioning and soft delete **off**. With them on, "Delete what I shared" would
only hide files.

## 5. The upload service

```powershell
az containerapp up --name $APP --resource-group $RG --location $LOC --source .\server --ingress external --target-port 8080 `
  --env-vars HISAAB_STORAGE=azure HISAAB_AZURE_CONTAINER=improve "HISAAB_AZURE_ACCOUNT_URL=https://$SA.blob.core.windows.net"
```

This builds the image in Azure, which takes a few minutes. It will say the app failed to
start. That's expected: the service refuses to run without its secret, which you add next.

```powershell
$SECRET = python -c "import secrets; print(secrets.token_urlsafe(48))"
az containerapp secret set --name $APP --resource-group $RG --secrets "hisaab-secret=$SECRET"
az containerapp update --name $APP --resource-group $RG --set-env-vars "HISAAB_SECRET=secretref:hisaab-secret" --min-replicas 0 --max-replicas 2
az containerapp identity assign --name $APP --resource-group $RG --system-assigned
$MI = az containerapp identity show --name $APP --resource-group $RG --query principalId -o tsv
# The service may write only to `improve`, plus make short upload links (Delegator). It can't touch `processed`.
az role assignment create --assignee-object-id $MI --assignee-principal-type ServicePrincipal --role "Storage Blob Data Contributor" --scope "$SAID/blobServices/default/containers/improve"
az role assignment create --assignee-object-id $MI --assignee-principal-type ServicePrincipal --role "Storage Blob Delegator" --scope $SAID
$URL = "https://" + (az containerapp show --name $APP --resource-group $RG --query properties.configuration.ingress.fqdn -o tsv); $URL
curl.exe "$URL/healthz"    # {"ok":true,...}
```

Role assignments can take a few minutes to start working.

## 6. The nightly job

```powershell
$ENVID = az containerapp show --name $APP --resource-group $RG --query properties.environmentId -o tsv
$IMAGE = az containerapp show --name $APP --resource-group $RG --query "properties.template.containers[0].image" -o tsv
$ACR = $IMAGE.Split("/")[0]
$KEY = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\hisaab-team-key.pem"))

az containerapp job create --name $JOB --resource-group $RG --environment $ENVID `
  --trigger-type Schedule --cron-expression "30 20 * * *" `
  --replica-timeout 7200 --replica-retry-limit 1 --parallelism 1 --replica-completion-count 1 `
  --image $IMAGE --cpu 1.0 --memory 2.0Gi --command "python" --args "-m" "hisaab_improve.pipeline" "nightly" `
  --mi-system-assigned --registry-server $ACR --registry-identity system `
  --secrets "team-key=$KEY" `
  --env-vars HISAAB_STORAGE=azure "HISAAB_AZURE_ACCOUNT_URL=https://$SA.blob.core.windows.net" HISAAB_AZURE_CONTAINER=improve `
             HISAAB_WORK_CONTAINER=processed HISAAB_PRIVATE_KEYS_B64=secretref:team-key HISAAB_RAW_DAYS=30

$JMI = az containerapp job show --name $JOB --resource-group $RG --query identity.principalId -o tsv
az role assignment create --assignee-object-id $JMI --assignee-principal-type ServicePrincipal --role "Storage Blob Data Contributor" --scope $SAID
Remove-Variable KEY
```

`30 20 * * *` is 20:30 UTC, which is 02:00 in India. To run it now and see what happened:

```powershell
az containerapp job start --name $JOB --resource-group $RG
az containerapp job execution list --name $JOB --resource-group $RG -o table
```

Each run's summary is in the job's logs (in the Azure portal: the job, then Execution history,
then Logs). A full record of each run is also kept in `processed/audit/`.

### Optional: mirror to Hugging Face

Use a Hugging Face token with **write** access to the private dataset:

```powershell
$HF = Read-Host "Hugging Face write token" -AsSecureString
az containerapp job secret set --name $JOB --resource-group $RG --secrets "hf-token=$([Net.NetworkCredential]::new('', $HF).Password)"
az containerapp job update --name $JOB --resource-group $RG --set-env-vars HF_TOKEN=secretref:hf-token HISAAB_HF_DATASET=AcxelinLabs/hisaabsaathi-voices
```

### Optional: a nightly message to the team

This works with any webhook that accepts `{"text": "..."}`, such as a Slack incoming webhook,
or a Teams workflow that posts the `text` field. The message holds counts only, never workers'
words.

```powershell
az containerapp job secret set --name $JOB --resource-group $RG --secrets "webhook=<webhook address>"
az containerapp job update --name $JOB --resource-group $RG --set-env-vars HISAAB_NOTIFY_WEBHOOK=secretref:webhook
```

## 7. Build the app that sends

Send the service address (`$URL`) and the **public** key to whoever builds the app (neither
is secret), or build it yourself:

```powershell
cd android
.\gradlew :app:assembleRelease "-Phisaab.shareUrl=$URL" "-Phisaab.shareKey=<public key>"
```

A build without both still asks workers, but sends nothing.

## Checking labels and held-back phones (from your computer)

Give yourself access once:

```powershell
az role assignment create --assignee (az ad signed-in-user show --query id -o tsv) --role "Storage Blob Data Contributor" --scope $SAID
```

Then, in `server` with the virtual environment from step 3:

```powershell
$env:HISAAB_STORAGE="azure"; $env:HISAAB_AZURE_ACCOUNT_URL="https://$SA.blob.core.windows.net"
$env:HISAAB_AZURE_CONTAINER="improve"; $env:HISAAB_WORK_CONTAINER="processed"
.venv\Scripts\python -m hisaab_improve.pipeline review-sheet review.csv
#   open review.csv in Excel: write "yes" under accept, or a better label under correct_label; save as CSV UTF-8
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

- **The service secret:** set the new value as `hisaab-secret`, and put the old one in
  `HISAAB_SECRET_PREVIOUS` for a few weeks. Phones keep working throughout.
- **The team key:** make a new one (step 3) and build the app with the new public key. Give
  the job both private keys, comma-separated, in `team-key` until no phone uses the old one.

## Cost

I expect a few dollars a month at the start, mostly the container registry. The service
scales to zero when idle, the job runs a few minutes a night, and storage costs very little.
