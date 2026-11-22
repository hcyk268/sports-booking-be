#!/bin/sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/lib/common.sh"
USAGE='Usage: sh scripts/test-storage-upload.sh --court-id UUID [--api-base URL] [--provider-email EMAIL] [--password PASSWORD]
Without credential options/environment, prompts for the owning provider email and password.'
# Wrappers select the expected storage provider; default preserves the R2 check.
storage_provider=${UAT_STORAGE_PROVIDER:-r2}
case $storage_provider in r2) storage_label=R2 ;; s3) storage_label=S3 ;; *) die 'UAT_STORAGE_PROVIDER must be r2 or s3' ;; esac
provider_email=${UAT_SEED_PROVIDER_EMAIL:-}
password=${UAT_SEED_PASSWORD:-}
parse_args "$@"
[ -n "$court_id" ] || die '--court-id is required'
init_runtime
require openssl
if [ -z "$provider_email" ]; then printf 'Email tai khoan PROVIDER so huu san: ' >&2; IFS= read -r provider_email; fi
if [ -z "$password" ]; then
    printf 'Mat khau: ' >&2
    tty_state=$(stty -g) || die 'Cannot hide password input; set UAT_SEED_PASSWORD'
    trap 'stty "$tty_state"; rm -rf -- "$work_dir"' EXIT
    stty -echo
    IFS= read -r password || { stty "$tty_state"; exit 1; }
    stty "$tty_state"
    printf '\n' >&2
    trap 'rm -rf -- "$work_dir"' EXIT
fi
provider=$(login "$provider_email" PROVIDER)
asset_id=; image_id=
cleanup() {
    if [ -n "$image_id" ]; then
        if api DELETE "/provider/courts/$court_id/images/$image_id" '' "$provider" >/dev/null; then log "CLEANUP: Test image removed; $storage_label deletion is queued"
        else log "CLEANUP REQUIRED: remove test image $image_id using the owner API"; fi
    elif [ -n "$asset_id" ]; then
        api DELETE "/uploads/$asset_id" '' "$provider" >/dev/null || log 'Pending upload will expire and be cleaned up.'
    fi
    rm -rf -- "$work_dir"
}
trap cleanup EXIT
# A valid 2x2 PNG, avoiding platform-specific graphics dependencies.
printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAC0lEQVR4nGNgQAcAABIAAXfx+gAAAAAASUVORK5CYII=' | openssl base64 -d -A > "$work_dir/storage-check.png"
size=$(wc -c < "$work_dir/storage-check.png" | tr -d ' ')
body=$(jq -nc --arg id "$court_id" --argjson size "$size" '{purpose:"COURT_IMAGE",resourceId:$id,fileName:"storage-check.png",contentType:"image/png",sizeBytes:$size}')
ticket=$(api POST /uploads "$body" "$provider" | jq -ec '.data')
asset_id=$(printf '%s' "$ticket" | jq -er '.uploadId')
validate_storage_url() {
    case $storage_provider in
        r2) pattern='^https://[a-zA-Z0-9.-]+\.r2\.cloudflarestorage\.com([/?]|$)' ;;
        s3) pattern='^https://([a-zA-Z0-9.-]+\.)?s3([.-][a-z0-9-]+)?\.amazonaws\.com(\.cn)?([/?]|$)' ;;
    esac
    printf '%s' "$1" | jq -Re --arg pattern "$pattern" 'test($pattern)' >/dev/null || die "Unexpected $storage_label storage host. Check STORAGE_PROVIDER on the backend."
}
validate_storage_url "$(printf '%s' "$ticket" | jq -er '.uploadUrl')"
storage_put "$ticket" "$work_dir/storage-check.png" image/png
log "PASS: Direct PUT to $storage_label"
verified=$(api POST "/uploads/$asset_id/complete" '' "$provider")
printf '%s' "$verified" | jq -e '.data.status == "READY"' >/dev/null || die 'Upload was not verified.'
log 'PASS: Backend verified object'
gallery=/provider/courts/$court_id/images
attached=$(api POST "$gallery" "$(jq -nc --arg id "$asset_id" '{uploadId:$id}')" "$provider")
image_id=$(printf '%s' "$attached" | jq -er '.data.id')
log 'PASS: Verified image attached to court'
access=$(api GET "$gallery/$image_id/access" '' "$provider")
url=$(printf '%s' "$access" | jq -er '.data.url')
validate_storage_url "$url"
result=$(curl -fsS --connect-timeout 15 --max-time 120 "$url" -o "$work_dir/read.png" -w '%{http_code} %{content_type}')
case $result in '200 image/png'*) ;; *) die "$storage_label image read failed." ;; esac
log "PASS: Private $storage_label read without application JWT"
log 'Browser CORS still needs checking in the frontend; this script cannot verify preflight.'
