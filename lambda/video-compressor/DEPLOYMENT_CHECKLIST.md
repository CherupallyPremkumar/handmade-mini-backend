# Deployment Checklist

Follow these steps in order. Check off each one as you complete it.

## Prerequisites
- [ ] AWS account access (you have this)
- [ ] AWS Console login
- [ ] Region set to **ap-south-1 (Mumbai)** in the top-right corner

## Step 1: Build FFmpeg layer (local)
```bash
mkdir -p ~/Desktop/ffmpeg-layer/bin
cd ~/Desktop/ffmpeg-layer
curl -LO https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz
tar xf ffmpeg-master-latest-linux64-gpl.tar.xz
cp ffmpeg-master-latest-linux64-gpl/bin/ffmpeg bin/
cp ffmpeg-master-latest-linux64-gpl/bin/ffprobe bin/
chmod +x bin/ffmpeg bin/ffprobe
zip -r ffmpeg-layer.zip bin
```
- [ ] `ffmpeg-layer.zip` created (~140 MB)

## Step 2: Upload to S3
- [ ] Create S3 bucket `dhanunjaiah-lambda-artifacts` in ap-south-1
- [ ] Upload `ffmpeg-layer.zip` to the bucket
- [ ] Copy the S3 URI (e.g. `s3://dhanunjaiah-lambda-artifacts/ffmpeg-layer.zip`)

## Step 3: Create Lambda Layer
- [ ] Lambda → Layers → Create layer
- [ ] Name: `ffmpeg`
- [ ] Upload from S3 URI
- [ ] Compatible runtime: Python 3.11
- [ ] Compatible architecture: x86_64
- [ ] Layer created, ARN copied (e.g. `arn:aws:lambda:ap-south-1:ACCOUNT:layer:ffmpeg:1`)

## Step 4: Create IAM role for Lambda execution
- [ ] IAM → Roles → Create role
- [ ] Trusted entity: Lambda
- [ ] Policy: `AWSLambdaBasicExecutionRole`
- [ ] Name: `dhanunjaiah-video-compressor-role`

## Step 5: Create Lambda function
- [ ] Lambda → Create function → Author from scratch
- [ ] Name: `dhanunjaiah-video-compressor`
- [ ] Runtime: Python 3.11
- [ ] Architecture: x86_64
- [ ] Execution role: `dhanunjaiah-video-compressor-role`

## Step 6: Configure function
- [ ] Configuration → General → Memory: **3008 MB**
- [ ] Configuration → General → Ephemeral storage: **2048 MB**
- [ ] Configuration → General → Timeout: **15 min 0 sec**
- [ ] Configuration → Concurrency → Reserved concurrency: **2**
- [ ] Layers → Add layer → Specify ARN → paste layer ARN from Step 3

## Step 7: Environment variables
Generate webhook secret first:
```bash
openssl rand -hex 32
```
Save this value — you need it here AND in backend env vars.

- [ ] R2_ENDPOINT = `https://{CF_ACCOUNT_ID}.r2.cloudflarestorage.com`
- [ ] R2_ACCESS_KEY_ID = (your CF_R2_ACCESS_KEY value)
- [ ] R2_SECRET_ACCESS_KEY = (your CF_R2_SECRET_KEY value)
- [ ] R2_BUCKET = `dhanunjaiah-media`
- [ ] R2_PUBLIC_DOMAIN = (your CF_R2_PUBLIC_DOMAIN value, e.g. `pub-xxx.r2.dev`)
- [ ] BACKEND_WEBHOOK_URL = `https://dev-api.dhanunjaiah.com/api/admin/videos/compression-done`
- [ ] BACKEND_WEBHOOK_SECRET = (the openssl random string)

## Step 8: Paste Python code
- [ ] Copy all contents of `lambda/video-compressor/lambda_function.py`
- [ ] Lambda → Code tab → replace `lambda_function.py` contents
- [ ] Click **Deploy**

## Step 9: Test Lambda
- [ ] Upload a test video to R2 at `temp-videos/test.mp4` (via S3 Console or your existing admin UI)
- [ ] Lambda → Test tab → Create new event
- [ ] Event JSON:
```json
{
  "productId": "SOME-REAL-PRODUCT-ID-FROM-DB",
  "tempKey": "temp-videos/test.mp4"
}
```
- [ ] Click Test
- [ ] Check CloudWatch logs for success
- [ ] Verify compressed file in R2 at `videos/SOME-REAL-PRODUCT-ID/test.mp4`
- [ ] Webhook may fail with 404 (expected — backend not yet deployed with webhook endpoint)

## Step 10: Grant EC2 permission to invoke Lambda

### 10a. Find your EC2 IAM role
- [ ] EC2 → Instances → click dev instance (65.1.139.43)
- [ ] Look at "IAM Role" — if empty, create one and attach it
- [ ] If present, note the role name

### 10b. Add Lambda invoke policy
- [ ] IAM → Roles → (your EC2 role) → Add permissions → Create inline policy
- [ ] JSON tab, paste:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "lambda:InvokeFunction",
    "Resource": "arn:aws:lambda:ap-south-1:ACCOUNT_ID:function:dhanunjaiah-video-compressor"
  }]
}
```
Replace `ACCOUNT_ID` with your real account number.
- [ ] Name: `invoke-video-compressor`
- [ ] Create

## Step 11: Add backend env vars

Copy these values — they go into your backend `.env.dev` file:
```bash
LAMBDA_VIDEO_COMPRESSOR_ARN=<paste Lambda function ARN from AWS Console>
LAMBDA_WEBHOOK_SECRET=<paste the openssl random string from Step 7>
AWS_REGION=ap-south-1
```

SSH into dev EC2:
```bash
cd ~/handmade-mini-backend
nano .env  # or vi
# add the 3 lines above
```

## Step 12: Redeploy backend with new env vars
- [ ] Push any dev commit (empty commit works) to trigger GitHub Actions
- [ ] OR manually restart backend after editing .env
- [ ] Wait for deployment
- [ ] Test: `curl https://dev-api.dhanunjaiah.com/api/products` returns 200
- [ ] Test: Upload a video via admin UI
- [ ] Verify status shows "Compressing" → "Ready" within 1-2 minutes

## Step 13: Monitor CloudWatch
- [ ] Lambda → Monitor tab
- [ ] Check invocation count, errors, duration
- [ ] Check CloudWatch logs for each invocation

## Step 14: Production deployment (later, when dev works)
- [ ] Repeat Steps 5-11 for prod Lambda (or use same Lambda if you want)
- [ ] Update prod backend .env with Lambda ARN + webhook secret
- [ ] Update Lambda BACKEND_WEBHOOK_URL to `https://api.dhanunjaiah.com/...`
- [ ] Merge dev → main → auto-deploys backend

## Rollback plan
If anything breaks:
1. Remove `LAMBDA_VIDEO_COMPRESSOR_ARN` from backend env (leave empty)
2. Restart backend
3. Video upload falls back to old behavior (no compression, uses temp URL directly)
4. Fix Lambda, re-enable env var when ready

No code changes or DB rollback needed.
