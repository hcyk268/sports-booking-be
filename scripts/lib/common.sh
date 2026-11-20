#!/bin/sh
# Shared POSIX shell helpers. Entry points set SCRIPT_DIR before sourcing.
set -eu
umask 077

die() { printf '%s\n' "$*" >&2; exit 1; }
log() { printf '%s\n' "$*" >&2; }
require() { command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"; }
api_base=${UAT_SEED_API_BASE:-http://localhost:8080/api/v1}
provider_email=${UAT_SEED_PROVIDER_EMAIL:-paysafe.ikegdn1@gmail.com}
customer_email=${UAT_SEED_CUSTOMER_EMAIL:-paysafe.jiwhva2@gmail.com}
password=${UAT_SEED_PASSWORD:-matkhauMANH123}
booking_date=
manifest_path=$SCRIPT_DIR/uat-seed-manifest.json
skip_payment=false
refresh_gallery=false
seed_first=false
simulate_ipn=false
sepay_secret=${SEPAY_SECRET_KEY:-}
ipn_wait_seconds=4
court_key=football-q9
html_path=$SCRIPT_DIR/sepay-checkout.html
publish_draft_courts=false
open_browser=false
court_id=
container=sports-booking-rabbitmq
virtual_host=/
max_messages=
max_bytes=

parse_args() {
    while [ "$#" -gt 0 ]; do
        case $1 in
            --help|-h) printf '%s\n' "$USAGE"; exit 0 ;;
            --skip-payment) skip_payment=true; shift ;;
            --refresh-gallery) refresh_gallery=true; shift ;;
            --seed-first) seed_first=true; shift ;;
            --simulate-ipn) simulate_ipn=true; shift ;;
            --publish-draft-courts) publish_draft_courts=true; shift ;;
            --open-browser) open_browser=true; shift ;;
            --api-base|--provider-email|--customer-email|--password|--booking-date|--manifest-path|--sepay-secret|--ipn-wait-seconds|--court-key|--html-path|--court-id|--container|--virtual-host|--max-messages|--max-bytes)
                [ "$#" -ge 2 ] || die "Missing value for $1"
                case $1 in
                    --api-base) api_base=$2 ;; --provider-email) provider_email=$2 ;;
                    --customer-email) customer_email=$2 ;; --password) password=$2 ;;
                    --booking-date) booking_date=$2 ;; --manifest-path) manifest_path=$2 ;;
                    --sepay-secret) sepay_secret=$2 ;; --ipn-wait-seconds) ipn_wait_seconds=$2 ;;
                    --court-key) court_key=$2 ;; --html-path) html_path=$2 ;;
                    --court-id) court_id=$2 ;; --container) container=$2 ;;
                    --virtual-host) virtual_host=$2 ;; --max-messages) max_messages=$2 ;;
                    --max-bytes) max_bytes=$2 ;;
                esac
                shift 2 ;;
            *) die "Unknown option: $1. Use --help." ;;
        esac
    done
    api_base=${api_base%/}
}

init_runtime() {
    require curl
    require jq
    work_dir=$(mktemp -d "${TMPDIR:-/tmp}/sports-booking.XXXXXXXX")
    trap 'rm -rf -- "$work_dir"' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
}

# Output only the response body on stdout; diagnostics go to stderr.
api() (
    method=$1; path=$2; body=${3:-}; token=${4:-}; key=${5:-}
    case $path in http://*|https://*) uri=$path ;; *) uri=$api_base$path ;; esac
    response_file=$(mktemp "$work_dir/response.XXXXXXXX")
    request_file=$(mktemp "$work_dir/request.XXXXXXXX")
    trap 'rm -f -- "$response_file" "$request_file"' EXIT
    set -- -sS --connect-timeout 15 --max-time 120 -X "$method" "$uri" -o "$response_file" -w '%{http_code}'
    [ -z "$token" ] || set -- "$@" -H "Authorization: Bearer $token"
    [ -z "$key" ] || set -- "$@" -H "Idempotency-Key: $key"
    if [ -n "$body" ]; then
        printf '%s' "$body" > "$request_file"
        set -- "$@" -H 'Content-Type: application/json; charset=utf-8' --data-binary "@$request_file"
    fi
    attempt=1
    while :; do
        status=$(curl "$@") || return 1
        if [ "$status" = 429 ] && [ "$attempt" -lt 6 ]; then
            log "Rate limited on $method $path; retry in $((5 * attempt))s"
            sleep "$((5 * attempt))"
            attempt=$((attempt + 1))
            continue
        fi
        case $status in
            2??) cat "$response_file"; return 0 ;;
            *) log "API $method $path failed ($status): $(cat "$response_file")"; return 1 ;;
        esac
    done
)

login() (
    email=$1; role=$2
    result=$(api POST /auth/login "$(jq -nc --arg email "$email" --arg password "$password" '{email:$email,password:$password}')") || return 1
    printf '%s' "$result" | jq -e --arg role "$role" '.data.user.roles | index($role) != null' >/dev/null || { log "Account $email missing role $role"; return 1; }
    printf '%s' "$result" | jq -er '.data.accessToken | select(type == "string" and length > 0)'
)

resolve_date() {
    if [ -z "$booking_date" ]; then
        booking_date=$(TZ=Asia/Ho_Chi_Minh date -d tomorrow +%F 2>/dev/null) ||
            booking_date=$(TZ=Asia/Ho_Chi_Minh date -v+1d +%F) || die 'Cannot determine tomorrow; pass --booking-date YYYY-MM-DD'
    fi
    printf '%s' "$booking_date" | jq -Re 'test("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")' >/dev/null || die 'Invalid --booking-date; use YYYY-MM-DD'
    # UTC parsing validates calendar dates without depending on the host timezone.
    printf '"%sT00:00:00Z"' "$booking_date" | jq -e 'fromdateiso8601 | strftime("%Y-%m-%d") == "'"$booking_date"'"' >/dev/null || die 'Invalid booking date'
}

run_seed() {
    set -- sh "$SCRIPT_DIR/seed-uat-data.sh" --api-base "$api_base" --provider-email "$provider_email" --customer-email "$customer_email" --password "$password" --manifest-path "$manifest_path"
    [ -z "$booking_date" ] || set -- "$@" --booking-date "$booking_date"
    "$@"
}

schedule_slots() {
    schedule_response=$(api GET "/courts/$1/schedule?date=$booking_date") || return 1
    printf '%s' "$schedule_response" | jq -ec '
        def localtime: sub("\\.[0-9]+$"; "") | split(":") | map(if length == 1 then "0" + . else . end) | if length == 2 then . + ["00"] else . end | join(":");
        [.data.slots[] | select((.closed | not) and .priceConfigured and .totalPrice != null) |
        {startTime:(.startTime|localtime),endTime:(.endTime|localtime),price:.totalPrice}]'
}

# Keep sensitive and transport headers out of presigned storage requests.
storage_put() (
    ticket=$1; image=$2; content_type=$3
    set -- -fsS --connect-timeout 15 --max-time 120 -X PUT -H "Content-Type: $content_type" --upload-file "$image"
    printf '%s' "$ticket" | jq -r '(.headers // {}) | to_entries[] | select((.key|ascii_downcase) as $k | ["content-type","content-length","authorization","cookie","host"] | index($k) | not) | "\(.key): \(.value)"' > "$work_dir/storage-headers"
    while IFS= read -r header; do set -- "$@" -H "$header"; done < "$work_dir/storage-headers"
    curl "$@" "$(printf '%s' "$ticket" | jq -er '.uploadUrl')" >/dev/null
)

is_conflict() { case $1 in *'(409)'*|*SLOT_UNAVAILABLE*|*NOT_BOOKABLE*) return 0 ;; *) return 1 ;; esac; }
