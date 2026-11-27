#!/usr/bin/env bash
set -euo pipefail

export APP_IMAGE="${1:?Pass the GHCR image digest}"
registry_user="${2:?Pass the GitHub actor}"
deploy_host="${3:?Pass the deployment host}"
app_dir=/opt/sports-booking/app
export APP_ENV_FILE="$app_dir/.env"
compose_file="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/compose.yaml"

exec 9>"$app_dir/.deploy.lock"
flock -w 30 9

compose=(docker compose --project-name sports-booking-backend --project-directory "$app_dir"
    --env-file "$APP_ENV_FILE" --file "$compose_file")
"${compose[@]}" config --quiet
docker network inspect sb-net >/dev/null

export DOCKER_CONFIG
DOCKER_CONFIG=$(mktemp -d)
trap 'rm -rf -- "$DOCKER_CONFIG"' EXIT
docker login ghcr.io -u "$registry_user" --password-stdin
"${compose[@]}" pull app

if ! "${compose[@]}" up -d --no-deps --pull never --wait --wait-timeout 150 app; then
    "${compose[@]}" ps --all app
    exit 1
fi

curl --noproxy '*' --fail --silent --show-error --max-time 15 \
    --connect-to "$deploy_host:443:127.0.0.1:443" \
    -o /dev/null "https://$deploy_host/api/v1/sports"

install -m 0644 "$compose_file" "$app_dir/.compose.yaml.next"
mv -f -- "$app_dir/.compose.yaml.next" "$app_dir/compose.yaml"
printf 'APP_IMAGE=%s\n' "$APP_IMAGE" > "$app_dir/.release.env.next"
mv -f -- "$app_dir/.release.env.next" "$app_dir/release.env"
