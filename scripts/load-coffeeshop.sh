#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SLEEP_SECONDS="${SLEEP_SECONDS:-2}"
ROUNDS="${ROUNDS:-3}"
MAX_POLLS="${MAX_POLLS:-40}"
COFFEE_ID_1="${COFFEE_ID_1:-1}"
COFFEE_ID_2="${COFFEE_ID_2:-2}"

log() {
  echo "$*"
}

api_request() {
  local method="$1"
  local path="$2"
  local data="${3:-}"

  if [[ -n "$data" ]]; then
    curl -sS -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json' -d "$data"
  else
    curl -sS -X "$method" "$BASE_URL$path"
  fi
}

json_field() {
  local json="$1"
  local field="$2"
  python - "$json" "$field" <<'PY'
import json, sys
obj = json.loads(sys.argv[1])
print(obj[sys.argv[2]])
PY
}

create_order_payload() {
  local round="$1"
  cat <<JSON
{"customerName":"Customer $round","items":[{"coffeeId":$COFFEE_ID_1,"quantity":1},{"coffeeId":$COFFEE_ID_2,"quantity":2}]}
JSON
}

wait_and_pickup_order() {
  local order_id="$1"

  for ((poll_index = 1; poll_index <= MAX_POLLS; poll_index++)); do
    local status_json status
    status_json="$(api_request GET "/coffeeshop/orders/$order_id")"
    status="$(json_field "$status_json" "status")"
    log "Order $order_id status poll $poll_index/$MAX_POLLS: $status"

    case "$status" in
    FERTIG)
      local pickup_json pickup_status
      pickup_json="$(api_request POST "/coffeeshop/orders/$order_id/pickup")"
      pickup_status="$(json_field "$pickup_json" "status")"
      log "Order $order_id pickup result: $pickup_status"
      [[ "$pickup_status" == "ABGEHOLT" ]] && return 0
      ;;
    ABGEHOLT)
      return 0
      ;;
    esac

    sleep "$SLEEP_SECONDS"
  done

  return 1
}

log "Using BASE_URL=$BASE_URL"

for ((round = 1; round <= ROUNDS; round++)); do
  log "Round $round/$ROUNDS"

  order_payload="$(create_order_payload "$round")"
  order_json="$(api_request POST /coffeeshop/orders "$order_payload")"
  log "Order: $order_json"

  order_id="$(json_field "$order_json" "id")"

  if ! wait_and_pickup_order "$order_id"; then
    echo "Order $order_id was not picked up within $MAX_POLLS polls" >&2
  fi
done

log "Done."
