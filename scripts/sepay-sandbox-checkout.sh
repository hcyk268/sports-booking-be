#!/bin/sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/lib/common.sh"
USAGE='Usage: sh scripts/sepay-sandbox-checkout.sh [--api-base URL] [--provider-email EMAIL] [--customer-email EMAIL] [--password PASSWORD] [--booking-date YYYY-MM-DD] [--court-key KEY] [--html-path FILE] [--manifest-path FILE] [--publish-draft-courts] [--seed-first] [--open-browser]'
parse_args "$@"
init_runtime
resolve_date
if [ "$publish_draft_courts" = true ]; then
    sh "$SCRIPT_DIR/publish-draft-courts.sh" --api-base "$api_base" --provider-email "$provider_email" --password "$password"
fi
[ "$seed_first" = false ] || run_seed
customer=$(login "$customer_email" CUSTOMER)
if [ -f "$manifest_path" ]; then
    court_id=$(jq -r --arg key "$court_key" '[.courts[] | select(.key == $key and .status == "ACTIVE")][0].id // empty' "$manifest_path")
fi
if [ -z "$court_id" ]; then
    search=$(api GET "/courts/search?date=$booking_date&page=0&size=20")
    court_id=$(printf '%s' "$search" | jq -er '.data.items[0].id // empty') || die 'Không có sân ACTIVE; chạy sh scripts/seed-uat-data.sh hoặc sh scripts/publish-draft-courts.sh (cần đủ giá/giờ).'
fi
schedule_slots "$court_id" | jq -c '.[]' > "$work_dir/slots"
booking=
while IFS= read -r slot; do
    body=$(printf '%s' "$slot" | jq -c --arg id "$court_id" --arg date "$booking_date" '{courtId:$id,slots:[{date:$date,startTime,endTime}]}')
    key=sepay-live-$court_id-$booking_date-$(printf '%s' "$slot" | jq -r '.startTime')
    if booking=$(api POST /bookings "$body" "$customer" "$key" 2> "$work_dir/hold-error"); then break; fi
    error=$(cat "$work_dir/hold-error")
    is_conflict "$error" || die "$error"
done < "$work_dir/slots"
[ -n "$booking" ] || die 'Không giữ được slot trống; đổi --booking-date'
booking_id=$(printf '%s' "$booking" | jq -er '.data.id')
checkout=$(api POST "/bookings/$booking_id/payments" '{"paymentMethod":"BANK_TRANSFER"}' "$customer" "sepay-live-pay-$booking_id")
printf '%s' "$checkout" | jq -r --arg title "SePay sandbox — $(printf '%s' "$booking" | jq -r '.data.bookingCode')" '
    .data.checkout | "<!DOCTYPE html>\n<html lang=\"vi\"><head><meta charset=\"utf-8\" /><title>\($title|@html)</title></head>\n<body onload=\"document.forms[0].submit()\">\n<p>Đang chuyển tới SePay sandbox…</p>\n<form method=\"\(.method|@html)\" action=\"\(.action|@html)\">\n" +
    ([.fields[] | "<input type=\"hidden\" name=\"\(.name|@html)\" value=\"\(.value|tostring|@html)\" />"] | join("\n")) +
    "\n<noscript><button type=\"submit\">Thanh toán SePay</button></noscript></form></body></html>"' > "$html_path"
log "Saved: $html_path"
log "Payment: $(printf '%s' "$checkout" | jq -r '.data.paymentId') | Booking: $booking_id"
log 'Mở file HTML để thanh toán SePay sandbox. Trạng thái cuối cần IPN; thanh toán trước khi booking hold hết hạn (~10 phút).'
if [ "$open_browser" = true ]; then
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "$html_path"
    elif command -v open >/dev/null 2>&1; then open "$html_path"
    elif command -v cygstart >/dev/null 2>&1; then cygstart "$html_path"
    else log "Không tìm thấy trình mở trình duyệt; mở thủ công: $html_path"; fi
fi
