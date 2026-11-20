#!/bin/sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/lib/common.sh"
USAGE='Usage: sh scripts/publish-draft-courts.sh [--api-base URL] [--provider-email EMAIL] [--password PASSWORD]'
parse_args "$@"
init_runtime
provider=$(login "$provider_email" PROVIDER)
page=0; published=0; skipped=0
while :; do
    response=$(api GET "/provider/courts?page=$page&size=50" '' "$provider")
    printf '%s' "$response" | jq -c '.data.items[]' > "$work_dir/courts"
    while IFS= read -r court; do
        if [ "$(printf '%s' "$court" | jq -r '.status')" != DRAFT ]; then
            skipped=$((skipped + 1)); continue
        fi
        id=$(printf '%s' "$court" | jq -er '.id')
        if api POST "/courts/$id/publish" '' "$provider" >/dev/null; then
            log "ACTIVE: $(printf '%s' "$court" | jq -r '.name') ($id)"
            published=$((published + 1))
        else log "Skip $id: publish failed"; fi
    done < "$work_dir/courts"
    [ "$(printf '%s' "$response" | jq -r '.data.pagination.hasNext')" = true ] || break
    page=$((page + 1))
done
log "Published: $published | Already non-DRAFT: $skipped"
log 'Sân ACTIVE cần đủ giá/giờ để lên search; chạy sh scripts/seed-uat-data.sh.'
