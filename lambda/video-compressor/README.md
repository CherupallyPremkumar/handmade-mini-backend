# Video Compressor Lambda

AWS Lambda function that compresses product videos from Cloudflare R2
using FFmpeg, reducing file size by ~95% (500 MB → ~25 MB).

## Architecture

```
Admin uploads video → R2 temp-videos/
Backend confirm-video → invokes Lambda (async)
Lambda downloads → FFmpeg compresses → R2 videos/ → deletes temp
Lambda → POST /api/admin/videos/compression-done (HMAC signed)
Backend updates product videoUrl + status = READY
```

## Manual setup (one-time)

### 1. Build the FFmpeg layer
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

### 2. Upload to S3 (one-time)
Console → S3 → Create bucket `dhanunjaiah-lambda-artifacts` (ap-south-1) →
Upload `ffmpeg-layer.zip`

### 3. Create Lambda Layer
Console → Lambda → Layers → Create layer
- Name: `ffmpeg`
- Upload from S3 URI of the zip
- Compatible runtimes: Python 3.11
- Compatible architectures: x86_64

### 4. Create IAM role
Console → IAM → Create role
- Trusted entity: Lambda
- Policy: `AWSLambdaBasicExecutionRole`
- Name: `dhanunjaiah-video-compressor-role`

### 5. Create Lambda function
Console → Lambda → Create function
- Name: `dhanunjaiah-video-compressor`
- Runtime: Python 3.11
- Architecture: x86_64
- Execution role: `dhanunjaiah-video-compressor-role`

Configuration:
- Memory: 3008 MB
- Ephemeral storage: 2048 MB
- Timeout: 15 min
- Reserved concurrency: 2

Add layer: paste the ffmpeg layer ARN from Step 3.

Environment variables:
| Key | Value |
|---|---|
| R2_ENDPOINT | `https://{CF_ACCOUNT_ID}.r2.cloudflarestorage.com` |
| R2_ACCESS_KEY_ID | (from CF_R2_ACCESS_KEY) |
| R2_SECRET_ACCESS_KEY | (from CF_R2_SECRET_KEY) |
| R2_BUCKET | `dhanunjaiah-media` |
| R2_PUBLIC_DOMAIN | (from CF_R2_PUBLIC_DOMAIN) |
| BACKEND_WEBHOOK_URL | `https://dev-api.dhanunjaiah.com/api/admin/videos/compression-done` |
| BACKEND_WEBHOOK_SECRET | Generate with `openssl rand -hex 32` |

Paste `lambda_function.py` contents into the code editor → Deploy.

### 6. Grant Lambda invoke permission to backend EC2
The EC2 IAM role needs `lambda:InvokeFunction` on this specific Lambda.
Add inline policy:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "lambda:InvokeFunction",
    "Resource": "arn:aws:lambda:ap-south-1:ACCOUNT:function:dhanunjaiah-video-compressor"
  }]
}
```

### 7. Configure backend env vars
Add to backend `.env.dev` and `.env.prod`:
```bash
LAMBDA_VIDEO_COMPRESSOR_ARN=arn:aws:lambda:ap-south-1:ACCOUNT:function:dhanunjaiah-video-compressor
LAMBDA_WEBHOOK_SECRET=<same secret as BACKEND_WEBHOOK_SECRET above>
```

## Test invocation

Lambda console → Test tab → Create new event:
```json
{
  "productId": "YOUR-REAL-PRODUCT-ID",
  "tempKey": "temp-videos/some-test-video.mp4"
}
```

Expected:
- CloudWatch log shows download, compression, upload, webhook call
- File in R2 `videos/YOUR-REAL-PRODUCT-ID/*.mp4`
- Backend product updated with new URL

## FFmpeg settings

Current compression preset:
```
-c:v libx264         H.264 codec (universal browser support)
-preset medium       speed vs size balance
-crf 26              quality level (lower=better, 26 is good for web)
-vf scale=-2:1080    1080p, auto width, keeps aspect ratio
-movflags +faststart enables progressive download/streaming
-c:a aac -b:a 128k   audio at 128 kbps
```

Expected output: ~22 MB per minute of video (from 500 MB).

## Cost estimate

| Item | Value |
|---|---|
| Lambda invocations | 10-20/month |
| Memory | 3008 MB |
| Duration | ~60s per video |
| Compute cost | $0.003 per video |
| Monthly total | ~$0.06/month |
| R2 storage saved | ~95% |

## Troubleshooting

**FFmpeg not found at /opt/bin/ffmpeg**
- Layer not attached to function, or zip structure wrong
- Zip must have `bin/ffmpeg` at root, becomes `/opt/bin/ffmpeg`

**R2 download fails**
- Check R2 credentials in env vars
- Verify `R2_ENDPOINT` has correct account ID
- Check bucket name matches

**Webhook returns 401/403**
- `BACKEND_WEBHOOK_SECRET` must match backend `LAMBDA_WEBHOOK_SECRET`
- Check backend has the webhook endpoint deployed

**Timeout after 15 min**
- Video too large; reject at presign step (max 1 GB)
- Or increase Lambda memory for faster CPU

**Lambda cold start slow**
- Normal: ~3-5s with layer attached
- First run after idle: downloads layer from AWS internal storage
- Warm runs: instant
