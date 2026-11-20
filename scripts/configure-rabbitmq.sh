#!/bin/sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "$SCRIPT_DIR/lib/common.sh"
USAGE='Usage: sh scripts/configure-rabbitmq.sh [--container NAME] [--virtual-host /] [--max-messages N] [--max-bytes N]'
parse_args "$@"
require docker
require jq
for value in "$max_messages" "$max_bytes"; do
    [ -z "$value" ] || printf '%s' "$value" | jq -Re 'test("^[0-9]+$") and (tonumber >= 1 and tonumber <= 2147483647)' >/dev/null || die 'Limits must be integers between 1 and 2147483647'
done
policy=$(jq -c --arg messages "$max_messages" --arg bytes "$max_bytes" '
    if $messages != "" then .definition["max-length"] = ($messages|tonumber) else . end |
    if $bytes != "" then .definition["max-length-bytes"] = ($bytes|tonumber) else . end' "$SCRIPT_DIR/../docker/rabbitmq/backlog-policy.json")
# Update existing queues without redeclaring immutable arguments.
docker exec "$container" rabbitmqctl set_policy -p "$virtual_host" \
    --priority "$(printf '%s' "$policy" | jq -r '.priority')" \
    --apply-to "$(printf '%s' "$policy" | jq -r '.["apply-to"]')" \
    sports-booking-bounded "$(printf '%s' "$policy" | jq -r '.pattern')" \
    "$(printf '%s' "$policy" | jq -c '.definition')"
docker exec "$container" rabbitmqctl list_policies -p "$virtual_host"
