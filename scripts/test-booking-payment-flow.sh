#!/bin/sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/lib/common.sh"
USAGE='Usage: sh scripts/test-booking-payment-flow.sh [--api-base URL] [--provider-email EMAIL] [--customer-email EMAIL] [--password PASSWORD] [--manifest-path FILE] [--seed-first] [--booking-date YYYY-MM-DD] [--simulate-ipn] [--sepay-secret SECRET] [--ipn-wait-seconds N]'
parse_args "$@"
init_runtime
case $ipn_wait_seconds in ''|*[!0-9]*) die '--ipn-wait-seconds must be a nonnegative integer' ;; esac
[ "$seed_first" = false ] || run_seed
[ -f "$manifest_path" ] || die "Manifest not found: $manifest_path. Run sh scripts/seed-uat-data.sh or use --seed-first."
primary=$(jq -ec '([.bookings[] | select(.key == "football-q9")][0] // .bookings[0]) // empty' "$manifest_path") || die 'Manifest has no bookings.'
booking_id=$(printf '%s' "$primary" | jq -er '.booking.id')
court_id=$(printf '%s' "$primary" | jq -er '.courtId')
booking_date=$(jq -er '.bookingDate' "$manifest_path")
payment_id=$(printf '%s' "$primary" | jq -r '.payment.paymentId // empty')
invoice_number=
customer=$(login "$customer_email" CUSTOMER)
provider=$(login "$provider_email" PROVIDER)
passed=0; failed=0

# Run each step in a fresh shell with errexit, then aggregate failures.
# Checking a function in an if statement disables errexit inside that function.
step() {
    log ">> $1"
    set +e
    sh -ec "$2" > "$work_dir/step-output" 2> "$work_dir/step-error"
    result=$?
    set -e
    if [ "$result" -eq 0 ]; then
        log '   OK'; passed=$((passed + 1))
    else
        log "   FAIL: $(cat "$work_dir/step-error")"
        printf '%s\n' "$1" >> "$work_dir/failed-steps"
        failed=$((failed + 1))
    fi
}
# Child steps load helpers using exported context, without serializing credentials into code.
export SCRIPT_DIR api_base work_dir customer provider booking_id court_id booking_date payment_id invoice_number
step 'GET /courts/search' '. "$SCRIPT_DIR/lib/flow-context.sh"; api GET "/courts/search?date=$booking_date&page=0&size=5" | jq -e ".data.pagination.totalElements >= 1"'
step 'GET /courts/{id}/schedule' '. "$SCRIPT_DIR/lib/flow-context.sh"; api GET "/courts/$court_id/schedule?date=$booking_date"'
step 'GET /bookings/me' '. "$SCRIPT_DIR/lib/flow-context.sh"; api GET "/bookings/me?page=0&size=20" "" "$customer"'
step 'GET /bookings/{id} (customer)' '. "$SCRIPT_DIR/lib/flow-context.sh"; api GET "/bookings/$booking_id" "" "$customer" | jq -e --arg id "$booking_id" ".data.id == \$id"'
step 'GET /bookings (provider)' '. "$SCRIPT_DIR/lib/flow-context.sh"; api GET "/bookings?page=0&size=10" "" "$provider"'
if [ -z "$payment_id" ]; then
    step 'POST /bookings/{id}/payments' '. "$SCRIPT_DIR/lib/flow-context.sh"; response=$(api POST "/bookings/$booking_id/payments" "{\"paymentMethod\":\"BANK_TRANSFER\"}" "$customer" "uat-flow-pay-$booking_id"); printf "%s" "$response" | jq -er ".data.paymentId" > "$work_dir/payment-id"'
    [ ! -f "$work_dir/payment-id" ] || payment_id=$(cat "$work_dir/payment-id")
fi
[ -n "$payment_id" ] || die "No payment id for remaining steps (passed: $passed, failed: $failed)"
export payment_id
step 'GET /bookings/{id}/payments' '. "$SCRIPT_DIR/lib/flow-context.sh"; api GET "/bookings/$booking_id/payments" "" "$customer"'
step 'GET /payments + GET /payments/{id}' '. "$SCRIPT_DIR/lib/flow-context.sh"; api GET "/payments?page=0&size=5" "" "$customer"; response=$(api GET "/payments/$payment_id" "" "$customer"); printf "%s" "$response" | jq -r ".data.attempts[0].invoiceNumber // empty" > "$work_dir/invoice-number"'
[ ! -f "$work_dir/invoice-number" ] || invoice_number=$(cat "$work_dir/invoice-number")
export invoice_number
step 'POST /payments/{id}/checkout' '. "$SCRIPT_DIR/lib/flow-context.sh"; api POST "/payments/$payment_id/checkout" "" "$customer"'
if [ "$simulate_ipn" = true ]; then
    if [ -z "$sepay_secret" ] && [ -f "$SCRIPT_DIR/../.env" ]; then
        # Read only this key; never execute .env as shell code.
        sepay_secret=$(sed -n 's/^[[:space:]]*SEPAY_SECRET_KEY=//p' "$SCRIPT_DIR/../.env" | head -n 1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//;s/^"//;s/"$//')
    fi
    export sepay_secret ipn_wait_seconds
    step 'POST /payments/sepay/ipn (simulated)' '. "$SCRIPT_DIR/lib/flow-context.sh"; sh "$SCRIPT_DIR/lib/simulate-ipn.sh"'
else log 'IPN skipped; use --simulate-ipn or pay via SePay sandbox.'; fi
log "Passed: $passed / $((passed + failed))"
if [ "$failed" -gt 0 ]; then cat "$work_dir/failed-steps" >&2; exit 1; fi
log "PaymentId: $payment_id | BookingId: $booking_id"
