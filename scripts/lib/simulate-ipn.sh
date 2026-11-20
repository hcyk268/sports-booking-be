#!/bin/sh
. "$SCRIPT_DIR/lib/flow-context.sh"
[ -n "$sepay_secret" ] || die 'SEPAY_SECRET_KEY required for --simulate-ipn (env, .env or --sepay-secret)'
[ -n "$invoice_number" ] || die 'Missing invoice number'
payment=$(api GET "/payments/$payment_id" '' "$customer")
status=$(printf '%s' "$payment" | jq -er '.data.status')
case $status in SUCCEEDED) exit 0 ;; PENDING|PROCESSING) ;; *) die "Payment status $status; cannot simulate IPN" ;; esac
amount=$(printf '%s' "$payment" | jq -r '.data.amount | tonumber | floor | tostring')
customer_id=$(api GET /users/me '' "$customer" | jq -er '.data.id')
txn=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
payload=$(jq -nc --arg id "$txn" --arg amount "$amount" --arg invoice "$invoice_number" --arg customer "$customer_id" --arg date "$(TZ=Asia/Ho_Chi_Minh date '+%Y-%m-%d %H:%M:%S')" --argjson timestamp "$(date +%s)" '{timestamp:$timestamp,notification_type:"ORDER_PAID",order:{id:("order-"+$id),order_id:("gw-"+$id),order_status:"CAPTURED",order_currency:"VND",order_amount:($amount+".00"),order_invoice_number:$invoice},transaction:{id:("txn-"+$id),transaction_id:("bank-"+$id),payment_method:"BANK_TRANSFER",transaction_type:"PAYMENT",transaction_status:"APPROVED",transaction_amount:$amount,transaction_currency:"VND",transaction_date:$date},customer:{id:$customer,customer_id:$customer}}')
curl -fsS --connect-timeout 15 --max-time 120 -X POST "$api_base/payments/sepay/ipn" -H "X-Secret-Key: $sepay_secret" -H 'Content-Type: application/json' --data-raw "$payload" >/dev/null
sleep "$ipn_wait_seconds"
after=$(api GET "/payments/$payment_id" '' "$customer")
printf '%s' "$after" | jq -e '.data.status == "SUCCEEDED"' >/dev/null || die "Expected SUCCEEDED, got $(printf '%s' "$after" | jq -r '.data.status')"
booking=$(api GET "/bookings/$booking_id" '' "$customer")
printf '%s' "$booking" | jq -e '.data.status == "CONFIRMED"' >/dev/null || die "Expected CONFIRMED booking, got $(printf '%s' "$booking" | jq -r '.data.status')"
