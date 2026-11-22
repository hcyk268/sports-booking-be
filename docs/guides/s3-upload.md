# Amazon S3 uploads

The existing [Upload API](../api/modules/upload-api.md) supports private Amazon S3 and Cloudflare R2 buckets. Frontend requests and responses stay the same: request a ticket, PUT the bytes with the returned headers, complete verification, then attach the upload ID.

## Application configuration

Create a private general purpose S3 bucket and set its actual region:

```dotenv
STORAGE_PROVIDER=s3
S3_ENABLED=true
S3_REGION=ap-southeast-1
S3_BUCKET_NAME=your-private-bucket
```

For explicit credentials, also set `S3_ACCESS_KEY_ID` and `S3_SECRET_ACCESS_KEY`. Temporary credentials additionally require `S3_SESSION_TOKEN`. Supplying only one key, or a session token without keys, fails startup. Do not commit credentials.

Leave all three `S3_*` credential values empty to use the [AWS SDK default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html), including standard `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN`, a shared AWS profile, or an IAM role on EC2/ECS/EKS. The AWS STS module is included for web identity and role assumption. `S3_REGION` falls back to `AWS_REGION` when unset. No AWS endpoint override or R2 endpoint is needed.

Presigned PUT URLs last 5 minutes; GET URLs last 60 seconds. With temporary credentials, URLs also expire when the underlying credentials expire. See [AWS presigned URL behavior](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html).

## Bucket permissions

Keep S3 Block Public Access enabled. Grant the application's IAM identity object access to these two prefixes (replace `your-private-bucket`):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
    "Resource": [
      "arn:aws:s3:::your-private-bucket/staging/*",
      "arn:aws:s3:::your-private-bucket/assets/*"
    ]
  }]
}
```

`HeadObject` and conditional validation reads use `s3:GetObject`; promotion copies from staging to assets using source read and destination write permissions. If the bucket uses a customer managed KMS key, also grant the identity the required KMS permissions and key policy access (`kms:Decrypt` and `kms:GenerateDataKey`). See [AWS operation permissions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-with-s3-policy-actions.html).

## Browser CORS

In S3 bucket Permissions → CORS, configure the actual frontend origins. For local development:

```json
[
  {
    "AllowedOrigins": ["http://localhost:3000"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["content-type", "x-amz-*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 300
  }
]
```

See [AWS CORS configuration](https://docs.aws.amazon.com/AmazonS3/latest/userguide/enabling-cors-examples.html). Send all ticket headers exactly as returned. Do not attach application JWTs, cookies, multipart form bodies, or a public ACL to the direct storage request.

## Staging lifecycle

Add a bucket lifecycle rule expiring objects under `staging/` after 1 day. Do not expire `assets/`: those keys hold attached images. The backend cleanup task deletes expired uploads and deletion-pending assets every 5 minutes; the lifecycle rule also removes staging objects rewritten through a still-valid PUT URL after backend cleanup.

Example lifecycle configuration (AWS CLI JSON format):

```json
{
  "Rules": [{
    "ID": "expire-upload-staging",
    "Status": "Enabled",
    "Filter": {"Prefix": "staging/"},
    "Expiration": {"Days": 1}
  }]
}
```

Use a bucket without versioning for this setup. On versioned buckets, additionally configure noncurrent version expiration and delete marker cleanup; ordinary DeleteObject and current-version expiration do not remove historical versions. See [AWS lifecycle expiration behavior](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-expire-general-considerations.html).

## Running alongside R2

Keep `R2_ENABLED=true` and the existing R2 credentials/bucket settings while enabling S3. Set `STORAGE_PROVIDER=s3` to send new uploads to S3. Migration `V26__upload_storage_provider.sql` marks existing upload rows as `R2`, and every new row records its selected provider. Completion, signed reads, and cleanup use that stored value. Switching back to `STORAGE_PROVIDER=r2` affects only new uploads.

The application does not move existing files. Keep a provider's bucket and region stable while its assets remain in use. If a provider is disabled, its files return `503 UPLOAD_UNAVAILABLE` for storage operations and cleanup leaves them pending until it is re-enabled. Both providers disabled keeps the application running with uploads unavailable. If any provider is enabled, selecting a disabled default fails startup.

## Smoke test

With a running backend configured to upload to S3 and an owned court UUID:

```sh
sh scripts/test-s3-upload.sh --court-id '<owned-court-uuid>'
```

The script requests a ticket, uploads a small PNG, completes verification, attaches it to the court, reads it through a signed private GET URL, then removes the gallery image and queues storage deletion. It needs POSIX `sh`, `curl`, `jq`, and `openssl`, and prompts for provider login credentials unless explicitly supplied. Browser CORS preflight still needs verification from the frontend. The existing `test-r2-upload.sh` performs the same check for an R2 default.
