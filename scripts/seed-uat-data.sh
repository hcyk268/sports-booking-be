#!/bin/sh
# Seed demo courts, R2 photos, booking holds and optional SePay checkout.
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/lib/common.sh"
USAGE='Usage: sh scripts/seed-uat-data.sh [--api-base URL] [--provider-email EMAIL] [--customer-email EMAIL] [--password PASSWORD] [--booking-date YYYY-MM-DD] [--skip-payment] [--refresh-gallery] [--manifest-path FILE]'
parse_args "$@"
init_runtime
resolve_date

update_profile() (
    token=$1; name=$2; phone=$3
    me=$(api GET /users/me '' "$token")
    if printf '%s' "$me" | jq -e --arg name "$name" --arg phone "$phone" '.data.fullName == $name and .data.phone == $phone' >/dev/null; then
        log "Profile OK: $name"; return
    fi
    body=$(printf '%s' "$me" | jq -c --arg name "$name" --arg phone "$phone" '{version:.data.version,fullName:$name,phone:$phone}')
    api PATCH /users/me "$body" "$token" >/dev/null
    log "Updated profile: $name"
)

find_court() (
    marker=uat-seed:$1; page=0
    while :; do
        response=$(api GET "/provider/courts?page=$page&size=50" '' "$provider") || return 1
        found=$(printf '%s' "$response" | jq -c --arg marker "$marker" '[.data.items[] | select((.description // "") | contains($marker))][0] // empty')
        if [ -n "$found" ]; then printf '%s' "$found"; return; fi
        [ "$(printf '%s' "$response" | jq -r '.data.pagination.hasNext')" = true ] || return 0
        page=$((page + 1))
    done
)

add_image() (
    id=$1; url=$2; filename=$3
    image=$(mktemp "$work_dir/image.XXXXXXXX") || return 1
    trap 'rm -f -- "$image"' EXIT
    curl -fLsS --connect-timeout 15 --max-time 120 --max-filesize 5242880 -A 'SportsBooking-Seed/1.0' "$url" -o "$image" || return 1
    size=$(wc -c < "$image" | tr -d ' ')
    [ "$size" -gt 0 ] && [ "$size" -le 5242880 ] || { log "Invalid image size: $size"; return 1; }
    body=$(jq -nc --arg id "$id" --arg name "$filename" --argjson size "$size" '{purpose:"COURT_IMAGE",resourceId:$id,fileName:$name,contentType:"image/jpeg",sizeBytes:$size}') || return 1
    ticket=$(api POST /uploads "$body" "$provider" | jq -ec '.data') || return 1
    storage_put "$ticket" "$image" image/jpeg || return 1
    upload_id=$(printf '%s' "$ticket" | jq -er '.uploadId') || return 1
    verified=$(api POST "/uploads/$upload_id/complete" '' "$provider") || return 1
    printf '%s' "$verified" | jq -e '.data.status == "READY"' >/dev/null || { log 'Upload not READY'; return 1; }
    api POST "/provider/courts/$id/images" "$(jq -nc --arg id "$upload_id" '{uploadId:$id}')" "$provider" >/dev/null
)

sync_gallery() (
    id=$1; definition=$2
    gallery=$(api GET "/provider/courts/$id/images" '' "$provider")
    count=$(printf '%s' "$gallery" | jq '.data | length')
    if [ "$count" -gt 0 ] && [ "$refresh_gallery" = false ]; then log "Gallery: $count image(s); use --refresh-gallery to add more"; return; fi
    max=$(printf '%s' "$definition" | jq '.maxImages')
    key=$(printf '%s' "$definition" | jq -r '.key')
    printf '%s' "$definition" | jq -r '.imageUrls[]' > "$work_dir/image-urls"
    index=0
    while IFS= read -r url; do
        [ "$count" -lt "$max" ] || break
        index=$((index + 1))
        if add_image "$id" "$url" "$key-photo-$index.jpg"; then count=$((count + 1)); else log "Gallery skip: $key-photo-$index.jpg"; fi
    done < "$work_dir/image-urls"
)

ensure_court() (
    definition=$1
    key=$(printf '%s' "$definition" | jq -r '.key')
    existing=$(find_court "$key")
    body=$(printf '%s' "$definition" | jq -c '{sportId,name,address,city,ward,description,amenities,rules}')
    if [ -n "$existing" ]; then
        id=$(printf '%s' "$existing" | jq -er '.id')
        body=$(printf '%s' "$body" | jq -c --argjson version "$(printf '%s' "$existing" | jq '.version')" '. + {version:$version}')
        api PUT "/courts/$id" "$body" "$provider" >/dev/null
        log "Updated court: $key ($id)"
    else
        created=$(api POST /courts "$body" "$provider")
        id=$(printf '%s' "$created" | jq -er '.data.id')
        log "Created court: $key ($id)"
    fi
    hours=$(printf '%s' "$definition" | jq -c '. as $d | {days:[(["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"][] | {dayOfWeek:.,openingTime:$d.weekdayOpen,closingTime:$d.weekdayClose,slotDurationMinutes:$d.slotMinutes}),(["SATURDAY","SUNDAY"][] | {dayOfWeek:.,openingTime:$d.weekendOpen,closingTime:$d.weekendClose,slotDurationMinutes:$d.slotMinutes})]}')
    api PUT "/provider/courts/$id/operating-hours" "$hours" "$provider" >/dev/null
    prices=$(printf '%s' "$definition" | jq -c '{rules:[.priceSegments[] | . as $s | .days[] | {dayOfWeek:.,startTime:$s.start,endTime:$s.end,pricePerHour:$s.price}]}')
    api PUT "/provider/courts/$id/price-rules" "$prices" "$provider" >/dev/null
    sync_gallery "$id" "$definition"
    detail=$(api GET "/provider/courts/$id" '' "$provider")
    if [ "$(printf '%s' "$detail" | jq -r '.data.status')" != ACTIVE ]; then
        api POST "/courts/$id/publish" '' "$provider" | jq -ec '.data'
    else printf '%s' "$detail" | jq -ec '.data'; fi
)

hold_booking() (
    id=$1; key=$2; count=$3; start=0
    while [ "$start" -lt 24 ]; do
        candidates=$(schedule_slots "$id")
        slots=$(printf '%s' "$candidates" | jq -c --argjson start "$start" --argjson count "$count" --arg date "$booking_date" '
            . as $all | [range($start; (($all|length) - $count + 1)) as $i | $all[$i:$i+$count] |
            select(. as $block | all(range(0; $count - 1); $block[.].endTime == $block[.+1].startTime))][0] // [] | map(. + {date:$date})')
        [ "$(printf '%s' "$slots" | jq length)" -eq "$count" ] || die "No $count consecutive bookable slots on $booking_date for $id"
        part=$(printf '%s' "$slots" | jq -r 'map(.startTime + "-" + .endTime) | join("_")')
        hold_key=uat-seed-hold-$key-$booking_date-$part
        [ "${#hold_key}" -le 100 ] || hold_key=uat-seed-hold-$key-$booking_date-$start
        body=$(printf '%s' "$slots" | jq -c --arg id "$id" '{courtId:$id,slots:map({date,startTime,endTime})}')
        if result=$(api POST /bookings "$body" "$customer" "$hold_key" 2> "$work_dir/hold-error"); then
            printf '%s' "$result" | jq -c --argjson slots "$slots" --arg key "$hold_key" '{booking:.data,slots:$slots,holdKey:$key}'
            return
        fi
        error=$(cat "$work_dir/hold-error")
        is_conflict "$error" || die "$error"
        start=$((start + 1))
    done
    die "Could not create booking for $key on $booking_date"
)

log "Realistic UAT seed -> $api_base | Booking date: $booking_date"
provider=$(login "$provider_email" PROVIDER)
customer=$(login "$customer_email" CUSTOMER)
update_profile "$provider" 'Trần Minh Khoa' 0908123456
update_profile "$customer" 'Lê Thị Mai' 0918765432
jq -nc --arg at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg base "$api_base" --arg provider "$provider_email" --arg customer "$customer_email" --arg date "$booking_date" '{seededAt:$at,apiBase:$base,providerEmail:$provider,customerEmail:$customer,bookingDate:$date,courts:[],bookings:[]}' > "$work_dir/manifest"
jq -c '.[]' "$SCRIPT_DIR/uat-courts.json" > "$work_dir/definitions"
while IFS= read -r definition; do
    court=$(ensure_court "$definition")
    id=$(printf '%s' "$court" | jq -er '.id')
    key=$(printf '%s' "$definition" | jq -r '.key')
    count=$(printf '%s' "$definition" | jq '[1,(.bookingSlots|length)]|max')
    hold=$(hold_booking "$id" "$key" "$count")
    booking_id=$(printf '%s' "$hold" | jq -er '.booking.id')
    entry=$(printf '%s' "$hold" | jq -c --argjson def "$definition" --arg id "$id" '{key:$def.key,courtId:$id,courtName:$def.name,city:$def.city,slots,booking:(.booking|{id,status,totalAmount,holdExpiresAt}) + {idempotencyKey:.holdKey},payment:null}')
    if [ "$skip_payment" = false ] && [ "$(printf '%s' "$definition" | jq '.createPayment')" = true ]; then
        pay_key=uat-seed-pay-$booking_id
        body=$(printf '%s' "$definition" | jq -c '{paymentMethod}')
        if checkout=$(api POST "/bookings/$booking_id/payments" "$body" "$customer" "$pay_key"); then
            entry=$(printf '%s' "$entry" | jq -c --argjson pay "$checkout" --arg key "$pay_key" '.payment = ($pay.data | {paymentId,attemptId,status,amount,idempotencyKey:$key,checkoutAction:.checkout.action,checkoutMethod:.checkout.method,fieldCount:(.checkout.fields|length)})')
            log "Payment checkout: $(printf '%s' "$checkout" | jq -r '.data.paymentId')"
        else log "Payment failed for $booking_id"; fi
    fi
    jq --argjson def "$definition" --argjson court "$court" --argjson entry "$entry" '.courts += [{key:$def.key,id:$court.id,name:$def.name,city:$def.city,status:$court.status}] | .bookings += [$entry]' "$work_dir/manifest" > "$work_dir/manifest-next"
    mv "$work_dir/manifest-next" "$work_dir/manifest"
    log "Booking: $booking_id | $key | $(printf '%s' "$entry" | jq -r '.booking.status')"
done < "$work_dir/definitions"
cp "$work_dir/manifest" "$manifest_path"
log "Done. Manifest: $manifest_path"
