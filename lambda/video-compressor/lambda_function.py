"""
Dhanunjaiah Video Compressor Lambda

Downloads a video from Cloudflare R2 temp folder, compresses it with FFmpeg
(H.264 1080p, ~95% smaller), uploads the compressed version to the final
location, deletes the temp file, and notifies the backend webhook.

Runtime: Python 3.11
Memory: 3008 MB (for FFmpeg CPU)
Timeout: 15 minutes
Layer: ffmpeg (static binary at /opt/bin/ffmpeg)

Environment variables:
  R2_ENDPOINT              - https://{account}.r2.cloudflarestorage.com
  R2_ACCESS_KEY_ID         - R2 S3-compatible access key
  R2_SECRET_ACCESS_KEY     - R2 S3-compatible secret
  R2_BUCKET                - e.g. dhanunjaiah-media
  R2_PUBLIC_DOMAIN         - e.g. pub-xxx.r2.dev
  BACKEND_WEBHOOK_URL      - https://{api}/api/admin/videos/compression-done
  BACKEND_WEBHOOK_SECRET   - shared secret for HMAC signature

Invocation payload:
  {
    "productId": "uuid",
    "tempKey": "temp-videos/uuid.mp4"
  }
"""

import os
import json
import hmac
import hashlib
import logging
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

# R2 client (S3-compatible)
s3 = boto3.client(
    's3',
    endpoint_url=os.environ['R2_ENDPOINT'],
    aws_access_key_id=os.environ['R2_ACCESS_KEY_ID'],
    aws_secret_access_key=os.environ['R2_SECRET_ACCESS_KEY'],
    region_name='auto',
)

BUCKET = os.environ['R2_BUCKET']
PUBLIC_DOMAIN = os.environ['R2_PUBLIC_DOMAIN']
WEBHOOK_URL = os.environ['BACKEND_WEBHOOK_URL']
WEBHOOK_SECRET = os.environ['BACKEND_WEBHOOK_SECRET']

FFMPEG = '/opt/bin/ffmpeg'


def lambda_handler(event, context):
    """
    Expected event:
      {
        "productId": "abc-123",
        "tempKey": "temp-videos/uuid.mp4"
      }
    """
    logger.info(f"Event: {json.dumps(event)}")

    product_id = event.get('productId')
    temp_key = event.get('tempKey')
    if not product_id or not temp_key:
        raise ValueError("Missing productId or tempKey in event")

    input_path = '/tmp/input.mp4'
    output_path = '/tmp/output.mp4'

    try:
        # Step 1: Download temp video from R2
        logger.info(f"Downloading {temp_key} from R2...")
        s3.download_file(BUCKET, temp_key, input_path)
        input_size = os.path.getsize(input_path)
        logger.info(f"Downloaded: {input_size / 1024 / 1024:.1f} MB")

        # Step 2: Run FFmpeg compression
        # Scale filter: cap at 1080p, but never upscale smaller videos.
        # Formula: if height > 1080 → scale to 1080, else keep original height.
        # Width auto-calculated to preserve aspect ratio, rounded to nearest even number.
        logger.info("Running FFmpeg compression...")
        cmd = [
            FFMPEG,
            '-i', input_path,
            '-c:v', 'libx264',
            '-preset', 'medium',
            '-crf', '26',
            '-vf', "scale='trunc(iw*min(1,1080/ih)/2)*2':'trunc(ih*min(1,1080/ih)/2)*2'",
            '-movflags', '+faststart',
            '-c:a', 'aac',
            '-b:a', '128k',
            '-y',
            output_path,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=840)
        if result.returncode != 0:
            raise RuntimeError(f"FFmpeg failed (exit {result.returncode}): {result.stderr[-500:]}")

        output_size = os.path.getsize(output_path)
        reduction_pct = (1 - output_size / input_size) * 100
        logger.info(
            f"Compressed: {output_size / 1024 / 1024:.1f} MB "
            f"({reduction_pct:.1f}% reduction)"
        )

        # Step 3: Upload compressed video to final location
        final_key = f"videos/{product_id}/{Path(temp_key).name}"
        logger.info(f"Uploading compressed to {final_key}...")
        s3.upload_file(
            output_path,
            BUCKET,
            final_key,
            ExtraArgs={'ContentType': 'video/mp4'},
        )

        # Step 4: Delete temp file
        logger.info(f"Deleting temp file {temp_key}...")
        s3.delete_object(Bucket=BUCKET, Key=temp_key)

        # Step 5: Notify backend
        compressed_url = f"https://{PUBLIC_DOMAIN}/{final_key}"
        notify_backend({
            'productId': product_id,
            'status': 'READY',
            'videoUrl': compressed_url,
            'originalSizeBytes': input_size,
            'compressedSizeBytes': output_size,
        })

        return {
            'status': 'success',
            'videoUrl': compressed_url,
            'originalSizeMB': round(input_size / 1024 / 1024, 1),
            'compressedSizeMB': round(output_size / 1024 / 1024, 1),
            'reductionPct': round(reduction_pct, 1),
        }

    except Exception as e:
        logger.exception("Compression failed")
        try:
            notify_backend({
                'productId': product_id,
                'status': 'FAILED',
                'error': str(e)[:500],
            })
        except Exception:
            logger.exception("Failed to notify backend about failure")
        raise

    finally:
        # Clean up /tmp — Lambda's ephemeral storage persists across warm
        # invocations, so any leftover files eat into our 2GB budget.
        import glob
        for f in glob.glob('/tmp/*'):
            try:
                if os.path.isfile(f):
                    os.remove(f)
            except Exception:
                pass


def notify_backend(payload):
    """POST to backend webhook with HMAC-SHA256 signature."""
    body = json.dumps(payload).encode('utf-8')
    signature = hmac.new(
        WEBHOOK_SECRET.encode('utf-8'),
        body,
        hashlib.sha256,
    ).hexdigest()

    req = urllib.request.Request(
        WEBHOOK_URL,
        data=body,
        method='POST',
        headers={
            'Content-Type': 'application/json',
            'X-Webhook-Signature': signature,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            logger.info(f"Webhook {payload['status']}: HTTP {resp.status}")
    except urllib.error.HTTPError as e:
        logger.error(f"Webhook failed: HTTP {e.code} - {e.read().decode()[:200]}")
        raise
    except Exception as e:
        logger.error(f"Webhook error: {e}")
        raise
