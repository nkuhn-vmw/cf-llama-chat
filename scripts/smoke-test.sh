#!/usr/bin/env bash
#
# Smoke test script for CF Llama Chat
# Runs against a live deployment to verify core functionality.
#
# Usage:
#   SMOKE_TEST_PASSWORD=secret bash scripts/smoke-test.sh
#   APP_URL=https://my-app.example.com SMOKE_TEST_PASSWORD=secret bash scripts/smoke-test.sh

set -euo pipefail

# --- Configuration ---
APP_URL="${APP_URL:-https://cf-llama-chat.apps.tas-tdc.kuhn-labs.com}"
USERNAME="admin"
PASSWORD="${SMOKE_TEST_PASSWORD:?SMOKE_TEST_PASSWORD env var is required}"

COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT

PASS_COUNT=0
FAIL_COUNT=0

# --- Helpers ---
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BOLD='\033[1m'
NC='\033[0m'

pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "  ${GREEN}✓${NC} $1"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo -e "  ${RED}✗${NC} $1"
    if [ -n "${2:-}" ]; then
        echo -e "    ${YELLOW}→ $2${NC}"
    fi
}

section() {
    echo ""
    echo -e "${BOLD}$1${NC}"
}

# Extract XSRF-TOKEN value from cookie jar file
get_csrf_token() {
    grep 'XSRF-TOKEN' "$COOKIE_JAR" | awk '{print $NF}' | tail -1
}

# --- Tests ---

section "1. Health Check"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "$APP_URL/actuator/health")
HEALTH_BODY=$(curl -s "$APP_URL/actuator/health")
if [ "$HTTP_CODE" = "200" ] && echo "$HEALTH_BODY" | grep -q '"UP"'; then
    pass "GET /actuator/health → 200 with UP"
else
    fail "GET /actuator/health → $HTTP_CODE" "$HEALTH_BODY"
fi

section "2. Unauthenticated Redirect"
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' -L --max-redirs 0 "$APP_URL/" 2>/dev/null || true)
LOCATION=$(curl -s -D - -o /dev/null "$APP_URL/" 2>/dev/null | grep -i '^location:' | tr -d '\r')
if echo "$LOCATION" | grep -qi 'login'; then
    pass "GET / without session redirects to login"
else
    fail "GET / without session did not redirect to login" "Location: $LOCATION (HTTP $HTTP_CODE)"
fi

section "3. Auth Provider"
PROVIDER_BODY=$(curl -s "$APP_URL/auth/provider")
if echo "$PROVIDER_BODY" | grep -q '"ssoEnabled"'; then
    pass "GET /auth/provider returns provider config"
else
    fail "GET /auth/provider unexpected response" "$PROVIDER_BODY"
fi

section "4. Login Flow"
# First hit a page to get an initial XSRF-TOKEN cookie
curl -s -o /dev/null -c "$COOKIE_JAR" "$APP_URL/login.html"

# Login via form POST (CSRF exempt for /auth/**)
LOGIN_RESPONSE=$(curl -s -D - -o /dev/null \
    -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
    -X POST "$APP_URL/auth/login" \
    --data-urlencode "username=${USERNAME}" \
    --data-urlencode "password=${PASSWORD}")

LOGIN_CODE=$(echo "$LOGIN_RESPONSE" | grep -o 'HTTP/[^ ]* [0-9]*' | tail -1 | awk '{print $2}')
LOGIN_LOCATION=$(echo "$LOGIN_RESPONSE" | grep -i '^location:' | tr -d '\r')

if echo "$LOGIN_LOCATION" | grep -q 'error'; then
    fail "POST /auth/login failed (redirected to error)" "$LOGIN_LOCATION"
elif grep -q 'JSESSIONID' "$COOKIE_JAR"; then
    pass "POST /auth/login succeeded, session cookie set"
else
    fail "POST /auth/login — no JSESSIONID in cookie jar" "HTTP $LOGIN_CODE, Location: $LOGIN_LOCATION"
fi

section "5. Security Headers"
HEADERS=$(curl -s -D - -o /dev/null -b "$COOKIE_JAR" -c "$COOKIE_JAR" "$APP_URL/")
for HEADER in "content-security-policy" "x-content-type-options" "x-frame-options" "referrer-policy" "permissions-policy"; do
    if echo "$HEADERS" | grep -qi "^${HEADER}:"; then
        pass "Header present: $HEADER"
    else
        fail "Header missing: $HEADER"
    fi
done

section "6. Auth Status"
CSRF_TOKEN=$(get_csrf_token)
AUTH_STATUS=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" "$APP_URL/auth/status")
if echo "$AUTH_STATUS" | grep -q '"authenticated":true'; then
    pass "GET /auth/status shows authenticated"
else
    fail "GET /auth/status not authenticated" "$AUTH_STATUS"
fi
if echo "$AUTH_STATUS" | grep -q "\"username\":\"${USERNAME}\""; then
    pass "Auth status shows correct username"
else
    fail "Auth status has unexpected username" "$AUTH_STATUS"
fi

section "7. List Models"
MODELS=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" "$APP_URL/api/chat/models")
if echo "$MODELS" | grep -q '"id"'; then
    MODEL_COUNT=$(echo "$MODELS" | grep -o '"id"' | wc -l | tr -d ' ')
    pass "GET /api/chat/models returns $MODEL_COUNT model(s)"
else
    fail "GET /api/chat/models unexpected response" "$MODELS"
fi

section "8. Create Conversation"
CSRF_TOKEN=$(get_csrf_token)
CREATE_CONV=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X POST "$APP_URL/api/conversations" \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
    -d '{"title":"Smoke Test Conversation"}')

CONV_ID=$(echo "$CREATE_CONV" | sed -n 's/.*"id" *: *"\([^"]*\)".*/\1/p' | head -1)
if [ -n "$CONV_ID" ]; then
    pass "POST /api/conversations created: $CONV_ID"
else
    fail "POST /api/conversations failed" "$CREATE_CONV"
fi

section "9. List Conversations"
CONVERSATIONS=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" "$APP_URL/api/conversations")
if echo "$CONVERSATIONS" | grep -q "$CONV_ID"; then
    pass "GET /api/conversations includes new conversation"
else
    fail "GET /api/conversations missing new conversation" "$CONVERSATIONS"
fi

section "10. Chat Completion"
CSRF_TOKEN=$(get_csrf_token)
CHAT_RESPONSE=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    --max-time 120 \
    -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
    -d "{\"conversationId\":\"$CONV_ID\",\"message\":\"Say hello in exactly 3 words.\",\"useDocumentContext\":false,\"useTools\":false}")

if echo "$CHAT_RESPONSE" | grep -q '"content"'; then
    pass "POST /api/chat returned a response"
else
    fail "POST /api/chat failed" "$CHAT_RESPONSE"
fi
if echo "$CHAT_RESPONSE" | grep -q '"complete":true'; then
    pass "Chat response is marked complete"
else
    fail "Chat response not marked complete" "$CHAT_RESPONSE"
fi

section "11. Chat with Invalid Model"
CSRF_TOKEN=$(get_csrf_token)
INVALID_MODEL_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -X POST "$APP_URL/api/chat" \
    -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
    -d '{"message":"test","model":"../../etc/passwd","useTools":false}')

if [ "$INVALID_MODEL_CODE" = "400" ]; then
    pass "POST /api/chat with path-traversal model rejected (400)"
else
    fail "POST /api/chat with invalid model returned $INVALID_MODEL_CODE (expected 400)"
fi

section "12. Document Status"
DOC_STATUS=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" "$APP_URL/api/documents/status")
if echo "$DOC_STATUS" | grep -q '"available"'; then
    pass "GET /api/documents/status returns status"
else
    fail "GET /api/documents/status unexpected response" "$DOC_STATUS"
fi

section "13. Admin Access"
ADMIN_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
    -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    "$APP_URL/admin")
if [ "$ADMIN_CODE" = "200" ]; then
    pass "GET /admin returns 200 (admin user)"
else
    fail "GET /admin returned $ADMIN_CODE (expected 200)"
fi

section "14. Delete Conversation (Cleanup)"
if [ -n "$CONV_ID" ]; then
    CSRF_TOKEN=$(get_csrf_token)
    DEL_CODE=$(curl -s -o /dev/null -w '%{http_code}' \
        -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
        -X DELETE "$APP_URL/api/conversations/$CONV_ID" \
        -H "X-XSRF-TOKEN: $CSRF_TOKEN")
    if [ "$DEL_CODE" = "204" ]; then
        pass "DELETE /api/conversations/$CONV_ID → 204"
    else
        fail "DELETE /api/conversations/$CONV_ID → $DEL_CODE (expected 204)"
    fi
else
    fail "Skipped — no conversation ID from earlier step"
fi

# --- Summary ---
echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
TOTAL=$((PASS_COUNT + FAIL_COUNT))
echo -e "${BOLD}Results:${NC} ${GREEN}$PASS_COUNT passed${NC}, ${RED}$FAIL_COUNT failed${NC} (out of $TOTAL)"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
fi
