#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"
BASE_URL="${BASE_URL%/}"
if [[ ! "$BASE_URL" =~ ^http://(127\.0\.0\.1|localhost)(:[0-9]+)?$ ]]; then
    echo 'Use a local URL such as http://127.0.0.1:8080.' >&2
    exit 1
fi
for dependency in curl jq; do
    command -v "$dependency" >/dev/null || { echo "Missing: $dependency" >&2; exit 1; }
done

TEST_DIR=$(mktemp -d)
COOKIE_FILE="$TEST_DIR/cookies"
RESPONSE_FILE="$TEST_DIR/response"
trap 'rm -f "$COOKIE_FILE" "$RESPONSE_FILE"; rmdir "$TEST_DIR"' EXIT
LOGIN_ID="auth_test_$(date +%s)_$$"

request() {
    local expected="$1" method="$2" path="$3"
    shift 3
    local http_code
    http_code=$(curl --silent --show-error --max-time 15 \
        -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o "$RESPONSE_FILE" -w '%{http_code}' \
        -X "$method" "$BASE_URL$path" "$@")
    if [[ ! "$http_code" =~ ^($expected)$ ]]; then
        echo "FAIL: $method $path expected $expected, got $http_code" >&2
        cat "$RESPONSE_FILE" >&2
        echo >&2
        exit 1
    fi
    echo "PASS: $http_code $method $path"
}

request 200 GET /api/dev/auth-data
BEFORE_COUNT=$(jq -er '.accounts' "$RESPONSE_FILE")
BEFORE_ORGANIZATIONS=$(jq -er '.organizations' "$RESPONSE_FILE")
BEFORE_MEMBERSHIPS=$(jq -er '.memberships' "$RESPONSE_FILE")
request 200 GET /api/auth/csrf
CSRF_TOKEN=$(jq -er '.token' "$RESPONSE_FILE")
SIGNUP_BODY=$(jq -n --arg loginId "$LOGIN_ID" '{
    onboardingType: "ORGANIZER", loginId: $loginId, password: "DevOnly!2026",
    nickname: "Auth test organizer", phoneNumber: "01000000003",
    email: "auth.test@example.com"
}')
request '401|403' POST /api/auth/sign-up -H 'Content-Type: application/json' -d "$SIGNUP_BODY"
CLAIM_BODY=$(jq -n --argjson body "$SIGNUP_BODY" '$body + {organizationName: "Student Council", role: "OWNER"}')
request 400 POST /api/auth/sign-up -H 'Content-Type: application/json' \
    -H "X-CSRF-TOKEN: $CSRF_TOKEN" -d "$CLAIM_BODY"
request 201 POST /api/auth/sign-up -H 'Content-Type: application/json' \
    -H "X-CSRF-TOKEN: $CSRF_TOKEN" -d "$SIGNUP_BODY"
ACCOUNT_ID=$(jq -er '.accountId' "$RESPONSE_FILE")
request 200 GET /api/dev/auth-data
jq -e --argjson before "$BEFORE_COUNT" '.accounts >= ($before + 1)' "$RESPONSE_FILE" >/dev/null
jq -e --argjson organizations "$BEFORE_ORGANIZATIONS" --argjson memberships "$BEFORE_MEMBERSHIPS" \
    '.organizations == $organizations and .memberships == $memberships' "$RESPONSE_FILE" >/dev/null
request 409 POST /api/auth/sign-up -H 'Content-Type: application/json' \
    -H "X-CSRF-TOKEN: $CSRF_TOKEN" -d "$SIGNUP_BODY"
request 200 GET "/api/auth/login-id-availability?loginId=$LOGIN_ID"
jq -e '.available == false' "$RESPONSE_FILE" >/dev/null
LOGIN_BODY=$(jq -n --arg loginId "$LOGIN_ID" '{loginId: $loginId, password: "DevOnly!2026"}')
request 200 POST /api/auth/login -H 'Content-Type: application/json' \
    -H "X-CSRF-TOKEN: $CSRF_TOKEN" -d "$LOGIN_BODY"
request 200 GET /api/me
jq -e --arg id "$ACCOUNT_ID" '.id == $id' "$RESPONSE_FILE" >/dev/null
request 200 GET /api/auth/csrf
CSRF_TOKEN=$(jq -er '.token' "$RESPONSE_FILE")
request 204 POST /api/auth/logout -H "X-CSRF-TOKEN: $CSRF_TOKEN"
request 401 GET /api/me
echo "Created login ID: $LOGIN_ID"
echo "Account ID: $ACCOUNT_ID"
echo 'The test account remains in the local DB for DBeaver inspection.'
