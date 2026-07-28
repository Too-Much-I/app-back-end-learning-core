#!/usr/bin/env bash

set -euo pipefail

readonly EXPECTED_AUDIENCE="tosunsaeng-learning-core"
IDENTITY_BASE_URL="${IDENTITY_BASE_URL:-http://localhost:8081}"
LEARNING_CORE_BASE_URL="${LEARNING_CORE_BASE_URL:-http://localhost:8080}"
IDENTITY_BASE_URL="${IDENTITY_BASE_URL%/}"
LEARNING_CORE_BASE_URL="${LEARNING_CORE_BASE_URL%/}"
readonly IDENTITY_BASE_URL LEARNING_CORE_BASE_URL
readonly EXPECTED_ISSUER="${IDENTITY_BASE_URL}"

CURRENT_STEP="초기화"
STEP_NUMBER=0
REQUEST_NUMBER=0
HTTP_STATUS=""
HTTP_BODY=""
LAST_RESPONSE_FILE=""
FAILURE_REPORTED=0
FIRST_CLEANUP_REFRESH_TOKEN=""
SECOND_CLEANUP_REFRESH_TOKEN=""

log() {
    printf '%s\n' "$*"
}

start_step() {
    STEP_NUMBER=$((STEP_NUMBER + 1))
    CURRENT_STEP="$1"
    printf '\n[%02d] %s\n' "${STEP_NUMBER}" "${CURRENT_STEP}"
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf '[FAIL] 필수 명령을 찾을 수 없습니다: %s\n' "$1" >&2
        exit 2
    fi
}

require_command curl
require_command jq

case "${E2E_KEEP_TEST_DATA:-false}" in
    1|true|TRUE|yes|YES)
        KEEP_TEST_DATA=true
        ;;
    0|false|FALSE|no|NO|'')
        KEEP_TEST_DATA=false
        ;;
    *)
        printf '[FAIL] E2E_KEEP_TEST_DATA는 true 또는 false여야 합니다.\n' >&2
        exit 2
        ;;
esac
readonly KEEP_TEST_DATA

if [[ -z "${IDENTITY_BASE_URL}" || -z "${LEARNING_CORE_BASE_URL}" ]]; then
    printf '[FAIL] 서버 Base URL은 비어 있을 수 없습니다.\n' >&2
    exit 2
fi

PASSWORD_VALUE="${E2E_TEST_PASSWORD:-}"
if [[ -z "${PASSWORD_VALUE}" ]]; then
    if [[ -t 0 && -t 2 ]]; then
        if ! IFS= read -r -s -p 'E2E 테스트 비밀번호를 입력하세요: ' PASSWORD_VALUE; then
            printf '\n[FAIL] 비밀번호 입력을 읽을 수 없습니다.\n' >&2
            exit 2
        fi
        printf '\n' >&2
    else
        printf '[FAIL] 비대화형 실행에서는 E2E_TEST_PASSWORD가 필요합니다.\n' >&2
        exit 2
    fi
fi

if (( ${#PASSWORD_VALUE} < 8 || ${#PASSWORD_VALUE} > 64 )); then
    printf '[FAIL] E2E 테스트 비밀번호는 8자 이상 64자 이하여야 합니다.\n' >&2
    exit 2
fi

umask 077
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tosunsaeng-auth-e2e.XXXXXX")"
readonly TEMP_DIR
PASSWORD_FILE="${TEMP_DIR}/password"
readonly PASSWORD_FILE
printf '%s' "${PASSWORD_VALUE}" >"${PASSWORD_FILE}"
PASSWORD_VALUE=""
unset E2E_TEST_PASSWORD

safe_response_summary() {
    local response_file="$1"

    if [[ ! -s "${response_file}" ]]; then
        printf '<empty response body>'
        return
    fi

    if jq -e . "${response_file}" >/dev/null 2>&1; then
        jq -c '
            if type == "object" then
                {
                    isSuccess: .isSuccess,
                    code: .code,
                    message: (
                        if (.message | type) == "string"
                        then .message[0:300]
                        else .message
                        end
                    ),
                    status: .status
                }
                | with_entries(select(.value != null))
            else
                {jsonType: type}
            end
        ' "${response_file}" 2>/dev/null || printf '<JSON summary unavailable>'
        return
    fi

    printf '<non-JSON response body omitted>'
}

fail() {
    local reason="$1"
    FAILURE_REPORTED=1
    printf '\n[FAIL] 단계: %s\n' "${CURRENT_STEP}" >&2
    printf '[FAIL] 원인: %s\n' "${reason}" >&2
    if [[ -n "${HTTP_STATUS}" ]]; then
        printf '[FAIL] HTTP 상태: %s\n' "${HTTP_STATUS}" >&2
    fi
    if [[ -n "${LAST_RESPONSE_FILE}" && -f "${LAST_RESPONSE_FILE}" ]]; then
        printf '[FAIL] 안전한 응답 요약: ' >&2
        safe_response_summary "${LAST_RESPONSE_FILE}" >&2
        printf '\n' >&2
    fi
    exit 1
}

write_refresh_request() {
    local refresh_token="$1"
    local output_file="$2"
    local raw_token_file="${output_file}.raw"

    printf '%s' "${refresh_token}" >"${raw_token_file}"
    if ! jq -n --rawfile refreshToken "${raw_token_file}" \
        '{refreshToken: $refreshToken}' >"${output_file}"; then
        fail 'Refresh Token 요청 JSON을 만들 수 없습니다.'
    fi
    : >"${raw_token_file}"
}

cleanup_refresh_session() {
    local refresh_token="$1"
    local cleanup_id="$2"
    local request_file="${TEMP_DIR}/cleanup-${cleanup_id}.json"
    local response_file="${TEMP_DIR}/cleanup-${cleanup_id}-response.json"
    local cleanup_status=""

    [[ -n "${refresh_token}" ]] || return 0
    write_refresh_request "${refresh_token}" "${request_file}"
    if ! cleanup_status="$(curl \
        --silent \
        --show-error \
        --connect-timeout 5 \
        --max-time 20 \
        --output "${response_file}" \
        --write-out '%{http_code}' \
        --request POST \
        --header 'Accept: application/json' \
        --header 'Content-Type: application/json' \
        --data-binary "@${request_file}" \
        "${IDENTITY_BASE_URL}/api/v1/auth/logout")"; then
        printf '[WARN] 실패 종료 후 Refresh Session 정리 요청에 연결하지 못했습니다.\n' >&2
        return 0
    fi

    if [[ "${cleanup_status}" != '200' ]]; then
        printf '[WARN] 실패 종료 후 Refresh Session 정리 HTTP 상태: %s\n' \
            "${cleanup_status}" >&2
    fi
}

cleanup_temp_dir() {
    if [[ -n "${TEMP_DIR:-}" \
        && -d "${TEMP_DIR}" \
        && "${TEMP_DIR}" == */tosunsaeng-auth-e2e.* ]]; then
        rm -rf -- "${TEMP_DIR}"
    fi
}

on_exit() {
    local exit_code=$?
    set +e

    if (( exit_code != 0 && FAILURE_REPORTED == 0 )); then
        printf '\n[FAIL] 단계: %s\n' "${CURRENT_STEP}" >&2
        printf '[FAIL] 예상하지 못한 스크립트 오류가 발생했습니다.\n' >&2
    fi

    if [[ "${KEEP_TEST_DATA}" == 'false' ]]; then
        cleanup_refresh_session "${FIRST_CLEANUP_REFRESH_TOKEN}" 'first'
        if [[ "${SECOND_CLEANUP_REFRESH_TOKEN}" != "${FIRST_CLEANUP_REFRESH_TOKEN}" ]]; then
            cleanup_refresh_session "${SECOND_CLEANUP_REFRESH_TOKEN}" 'second'
        fi
    fi

    cleanup_temp_dir
    trap - EXIT
    exit "${exit_code}"
}
trap on_exit EXIT

http_request() {
    local method="$1"
    local url="$2"
    local request_file="${3:-}"
    local auth_header_file="${4:-}"
    local response_file
    local -a curl_args

    REQUEST_NUMBER=$((REQUEST_NUMBER + 1))
    response_file="${TEMP_DIR}/response-${REQUEST_NUMBER}.json"
    curl_args=(
        --silent
        --show-error
        --connect-timeout 5
        --max-time 45
        --output "${response_file}"
        --write-out '%{http_code}'
        --request "${method}"
        --header 'Accept: application/json'
    )
    if [[ -n "${request_file}" ]]; then
        curl_args+=(
            --header 'Content-Type: application/json'
            --data-binary "@${request_file}"
        )
    fi
    if [[ -n "${auth_header_file}" ]]; then
        curl_args+=(--header "@${auth_header_file}")
    fi

    HTTP_STATUS=""
    HTTP_BODY="${response_file}"
    LAST_RESPONSE_FILE="${response_file}"
    if ! HTTP_STATUS="$(curl "${curl_args[@]}" "${url}")"; then
        fail 'HTTP 요청 대상 서버에 연결할 수 없습니다.'
    fi
}

expect_http_status() {
    local expected_status="$1"
    if [[ "${HTTP_STATUS}" != "${expected_status}" ]]; then
        fail "예상 HTTP ${expected_status}, 실제 HTTP ${HTTP_STATUS}"
    fi
}

expect_base_response() {
    local expected_success="$1"
    local expected_code="$2"

    if ! jq -e \
        --argjson expectedSuccess "${expected_success}" \
        --arg expectedCode "${expected_code}" \
        'type == "object"
         and .isSuccess == $expectedSuccess
         and .code == $expectedCode' \
        "${HTTP_BODY}" >/dev/null 2>&1; then
        fail "BaseResponse의 isSuccess/code가 예상과 다릅니다: ${expected_code}"
    fi
}

extract_required_string() {
    local input_file="$1"
    local jq_path="$2"
    local description="$3"
    local value=""

    if ! value="$(jq -er \
        "${jq_path} | select(type == \"string\" and length > 0)" \
        "${input_file}" 2>/dev/null)"; then
        fail "응답에서 ${description}을(를) 찾을 수 없습니다."
    fi
    printf '%s' "${value}"
}

assert_uuid() {
    local value="$1"
    local description="$2"
    local uuid_pattern='^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$'

    if [[ ! "${value}" =~ ${uuid_pattern} ]]; then
        fail "${description}이(가) UUID 형식이 아닙니다."
    fi
}

write_signup_request() {
    local email="$1"
    local nickname="$2"
    local output_file="$3"

    if ! jq -n \
        --arg email "${email}" \
        --rawfile password "${PASSWORD_FILE}" \
        --arg nickname "${nickname}" \
        '{
            email: $email,
            password: $password,
            nickname: $nickname,
            isAudioConsent: true
        }' >"${output_file}"; then
        fail '회원가입 요청 JSON을 만들 수 없습니다.'
    fi
}

write_login_request() {
    local email="$1"
    local output_file="$2"

    if ! jq -n \
        --arg email "${email}" \
        --rawfile password "${PASSWORD_FILE}" \
        '{email: $email, password: $password}' >"${output_file}"; then
        fail '로그인 요청 JSON을 만들 수 없습니다.'
    fi
}

write_bearer_header() {
    local access_token="$1"
    local output_file="$2"
    printf 'Authorization: Bearer %s\n' "${access_token}" >"${output_file}"
}

decode_jwt_part() {
    local token="$1"
    local part="$2"
    local output_file="$3"
    local jwt_header=""
    local jwt_payload=""
    local jwt_signature=""
    local jwt_dots=""
    local encoded_part=""

    jwt_dots="${token//[^.]/}"
    if (( ${#jwt_dots} != 2 )); then
        fail 'Identity가 반환한 Access Token이 JWT 3-part 형식이 아닙니다.'
    fi
    IFS='.' read -r jwt_header jwt_payload jwt_signature <<<"${token}"
    if [[ -z "${jwt_header}" || -z "${jwt_payload}" || -z "${jwt_signature}" ]]; then
        fail 'Identity가 반환한 Access Token에 빈 JWT part가 있습니다.'
    fi

    case "${part}" in
        header) encoded_part="${jwt_header}" ;;
        payload) encoded_part="${jwt_payload}" ;;
        *) fail "지원하지 않는 JWT part입니다: ${part}" ;;
    esac

    if ! printf '%s' "${encoded_part}" | jq -R '
        def padded:
            if (length % 4) == 0 then .
            elif (length % 4) == 2 then . + "=="
            elif (length % 4) == 3 then . + "="
            else error("invalid base64url length")
            end;
        gsub("-"; "+")
        | gsub("_"; "/")
        | padded
        | @base64d
        | fromjson
    ' >"${output_file}" 2>/dev/null; then
        fail "JWT ${part}를 안전하게 디코딩할 수 없습니다."
    fi
}

tamper_jwt_signature() {
    local token="$1"
    local jwt_header=""
    local jwt_payload=""
    local jwt_signature=""
    local replacement='A'

    IFS='.' read -r jwt_header jwt_payload jwt_signature <<<"${token}"
    if [[ -z "${jwt_signature}" ]]; then
        fail '변조할 JWT signature가 없습니다.'
    fi
    if [[ "${jwt_signature:0:1}" == 'A' ]]; then
        replacement='B'
    fi
    printf '%s.%s.%s%s' \
        "${jwt_header}" \
        "${jwt_payload}" \
        "${replacement}" \
        "${jwt_signature:1}"
}

expect_refresh_failure() {
    local expected_code="$1"
    expect_http_status '401'
    expect_base_response false "${expected_code}"
}

readonly RUN_TAG="$(date -u '+%Y%m%d%H%M%S')-$$-${RANDOM}"
readonly FIRST_EMAIL="e2e-auth-${RUN_TAG}-a@example.com"
readonly SECOND_EMAIL="e2e-auth-${RUN_TAG}-b@example.com"
readonly FIRST_NICKNAME="e2e-a-${RANDOM}"
readonly SECOND_NICKNAME="e2e-b-${RANDOM}"

start_step '서버 health, JWKS, 접근 가능 여부 확인'
http_request GET "${IDENTITY_BASE_URL}/actuator/health"
expect_http_status '200'
if ! jq -e '.status == "UP"' "${HTTP_BODY}" >/dev/null 2>&1; then
    fail 'Identity health 상태가 UP이 아닙니다.'
fi

http_request GET "${IDENTITY_BASE_URL}/.well-known/jwks.json"
expect_http_status '200'
JWKS_FILE="${HTTP_BODY}"
readonly JWKS_FILE
if ! jq -e '
    (.keys | type == "array")
    and (.keys | length > 0)
    and all(.keys[];
        .kty == "RSA"
        and .use == "sig"
        and .alg == "RS256"
        and (.kid | type == "string" and length > 0)
        and (.n | type == "string" and length > 0)
        and (.e | type == "string" and length > 0)
        and (has("d") | not)
        and (has("p") | not)
        and (has("q") | not)
        and (has("dp") | not)
        and (has("dq") | not)
        and (has("qi") | not)
    )
' "${JWKS_FILE}" >/dev/null 2>&1; then
    fail 'JWKS RSA 공개키 계약 또는 Private Key 파라미터 비노출 조건이 맞지 않습니다.'
fi

http_request GET "${LEARNING_CORE_BASE_URL}/"
if [[ ! "${HTTP_STATUS}" =~ ^[1-5][0-9][0-9]$ ]]; then
    fail 'Learning Core가 유효한 HTTP 응답을 반환하지 않았습니다.'
fi
log '[PASS] 두 서버와 Public JWKS에 접근할 수 있습니다.'

start_step '첫 번째 사용자 회원가입, 로그인, 내 프로필 조회'
FIRST_SIGNUP_REQUEST="${TEMP_DIR}/first-signup.json"
FIRST_LOGIN_REQUEST="${TEMP_DIR}/first-login.json"
write_signup_request "${FIRST_EMAIL}" "${FIRST_NICKNAME}" "${FIRST_SIGNUP_REQUEST}"
http_request POST "${IDENTITY_BASE_URL}/api/v1/auth/signup" "${FIRST_SIGNUP_REQUEST}"
expect_http_status '200'
expect_base_response true 'SUCCESS'
FIRST_SIGNUP_USER_ID="$(extract_required_string \
    "${HTTP_BODY}" '.result.userId' '회원가입 userId')"
assert_uuid "${FIRST_SIGNUP_USER_ID}" '회원가입 userId'

write_login_request "${FIRST_EMAIL}" "${FIRST_LOGIN_REQUEST}"
http_request POST "${IDENTITY_BASE_URL}/api/v1/auth/login" "${FIRST_LOGIN_REQUEST}"
expect_http_status '200'
expect_base_response true 'SUCCESS'
FIRST_ACCESS_TOKEN="$(extract_required_string \
    "${HTTP_BODY}" '.result.accessToken' 'Access Token')"
FIRST_REFRESH_TOKEN="$(extract_required_string \
    "${HTTP_BODY}" '.result.refreshToken' 'Refresh Token')"
FIRST_CLEANUP_REFRESH_TOKEN="${FIRST_REFRESH_TOKEN}"
FIRST_AUTH_HEADER="${TEMP_DIR}/first-auth.header"
write_bearer_header "${FIRST_ACCESS_TOKEN}" "${FIRST_AUTH_HEADER}"

http_request GET "${IDENTITY_BASE_URL}/api/v1/users/me" '' "${FIRST_AUTH_HEADER}"
expect_http_status '200'
expect_base_response true 'SUCCESS'
FIRST_USER_ID="$(extract_required_string "${HTTP_BODY}" '.result.userId' 'users/me userId')"
assert_uuid "${FIRST_USER_ID}" 'users/me userId'
if [[ "${FIRST_SIGNUP_USER_ID}" != "${FIRST_USER_ID}" ]]; then
    fail '회원가입 userId와 users/me userId가 다릅니다.'
fi
log '[PASS] 첫 번째 사용자 인증 Token을 발급하고 users/me를 확인했습니다. (Token 비출력)'

start_step 'Access Token Header와 Claim 계약 확인'
JWT_HEADER_FILE="${TEMP_DIR}/jwt-header.json"
JWT_PAYLOAD_FILE="${TEMP_DIR}/jwt-payload.json"
decode_jwt_part "${FIRST_ACCESS_TOKEN}" header "${JWT_HEADER_FILE}"
decode_jwt_part "${FIRST_ACCESS_TOKEN}" payload "${JWT_PAYLOAD_FILE}"
if ! jq -e '
    .alg == "RS256"
    and (.kid | type == "string" and length > 0)
' "${JWT_HEADER_FILE}" >/dev/null 2>&1; then
    fail 'Access Token Header의 alg 또는 kid 계약이 맞지 않습니다.'
fi
TOKEN_KID="$(extract_required_string "${JWT_HEADER_FILE}" '.kid' 'JWT kid')"
if ! jq -e --arg kid "${TOKEN_KID}" \
    'any(.keys[]; .kid == $kid)' "${JWKS_FILE}" >/dev/null 2>&1; then
    fail 'Access Token kid와 일치하는 JWKS 공개키가 없습니다.'
fi
if ! jq -e \
    --arg sub "${FIRST_USER_ID}" \
    --arg issuer "${EXPECTED_ISSUER}" \
    --arg audience "${EXPECTED_AUDIENCE}" '
        .sub == $sub
        and .iss == $issuer
        and (
            .aud == $audience
            or ((.aud | type) == "array" and (.aud | index($audience)) != null)
        )
        and (.iat | type == "number")
        and (.exp | type == "number")
        and .exp > .iat
        and (.jti | type == "string" and length > 0)
        and (
            .scope
            | type == "string"
            and length > 0
            and test("^[^[:space:]]+( [^[:space:]]+)*$")
        )
    ' "${JWT_PAYLOAD_FILE}" >/dev/null 2>&1; then
    fail 'Access Token의 sub/iss/aud/iat/exp/jti/scope 계약이 맞지 않습니다.'
fi
log '[PASS] RS256/kid와 필수 Claim을 로컬 디코딩으로 확인했습니다. (서명 검증은 Learning Core 담당)'

start_step 'Learning Core 무토큰 요청 거부'
http_request POST "${LEARNING_CORE_BASE_URL}/api/v1/exams"
expect_http_status '401'
expect_base_response false 'COMMON401'
log '[PASS] 무토큰 시험 생성 요청이 BaseResponse COMMON401로 거부됐습니다.'

start_step '유효한 Access Token으로 시험 생성'
http_request POST "${LEARNING_CORE_BASE_URL}/api/v1/exams" '' "${FIRST_AUTH_HEADER}"
expect_http_status '200'
expect_base_response true 'COMMON_200'
EXAM_ID="$(extract_required_string "${HTTP_BODY}" '.result.examId' 'examId')"
log '[PASS] 시험을 생성하고 examId를 확보했습니다. (전체 응답과 URL 비출력)'

start_step '동일 사용자 시험 상태 조회'
http_request GET \
    "${LEARNING_CORE_BASE_URL}/api/v1/exams/${EXAM_ID}/status" \
    '' "${FIRST_AUTH_HEADER}"
expect_http_status '200'
expect_base_response true 'COMMON_200'
if ! jq -e --arg examId "${EXAM_ID}" \
    '.result.examId == $examId' "${HTTP_BODY}" >/dev/null 2>&1; then
    fail '시험 상태 응답의 examId가 생성한 시험과 다릅니다.'
fi
log '[PASS] 첫 번째 사용자가 자신의 시험 상태를 조회했습니다.'

start_step '두 번째 사용자와 시험 소유권 격리'
SECOND_SIGNUP_REQUEST="${TEMP_DIR}/second-signup.json"
SECOND_LOGIN_REQUEST="${TEMP_DIR}/second-login.json"
write_signup_request "${SECOND_EMAIL}" "${SECOND_NICKNAME}" "${SECOND_SIGNUP_REQUEST}"
http_request POST "${IDENTITY_BASE_URL}/api/v1/auth/signup" "${SECOND_SIGNUP_REQUEST}"
expect_http_status '200'
expect_base_response true 'SUCCESS'

write_login_request "${SECOND_EMAIL}" "${SECOND_LOGIN_REQUEST}"
http_request POST "${IDENTITY_BASE_URL}/api/v1/auth/login" "${SECOND_LOGIN_REQUEST}"
expect_http_status '200'
expect_base_response true 'SUCCESS'
SECOND_ACCESS_TOKEN="$(extract_required_string \
    "${HTTP_BODY}" '.result.accessToken' '두 번째 Access Token')"
SECOND_REFRESH_TOKEN="$(extract_required_string \
    "${HTTP_BODY}" '.result.refreshToken' '두 번째 Refresh Token')"
SECOND_CLEANUP_REFRESH_TOKEN="${SECOND_REFRESH_TOKEN}"
SECOND_AUTH_HEADER="${TEMP_DIR}/second-auth.header"
write_bearer_header "${SECOND_ACCESS_TOKEN}" "${SECOND_AUTH_HEADER}"

http_request GET \
    "${LEARNING_CORE_BASE_URL}/api/v1/exams/${EXAM_ID}/status" \
    '' "${SECOND_AUTH_HEADER}"
expect_http_status '403'
expect_base_response false 'COMMON403'
log '[PASS] 두 번째 사용자의 첫 번째 사용자 시험 조회가 COMMON403으로 거부됐습니다.'

start_step '잘못된 Token과 Token 없음 거부'
RANDOM_TOKEN_HEADER="${TEMP_DIR}/random-token.header"
write_bearer_header "not-a-jwt-${RUN_TAG}" "${RANDOM_TOKEN_HEADER}"
http_request POST "${LEARNING_CORE_BASE_URL}/api/v1/exams" '' "${RANDOM_TOKEN_HEADER}"
expect_http_status '401'
expect_base_response false 'COMMON401'

TAMPERED_ACCESS_TOKEN="$(tamper_jwt_signature "${FIRST_ACCESS_TOKEN}")"
TAMPERED_TOKEN_HEADER="${TEMP_DIR}/tampered-token.header"
write_bearer_header "${TAMPERED_ACCESS_TOKEN}" "${TAMPERED_TOKEN_HEADER}"
http_request POST "${LEARNING_CORE_BASE_URL}/api/v1/exams" '' "${TAMPERED_TOKEN_HEADER}"
expect_http_status '401'
expect_base_response false 'COMMON401'

http_request POST "${LEARNING_CORE_BASE_URL}/api/v1/exams"
expect_http_status '401'
expect_base_response false 'COMMON401'
log '[PASS] 임의 문자열, signature 변조, Token 없음 요청이 모두 COMMON401로 거부됐습니다.'

start_step 'Refresh Token Rotation과 재사용 탐지'
REISSUE_REQUEST="${TEMP_DIR}/reissue.json"
write_refresh_request "${FIRST_REFRESH_TOKEN}" "${REISSUE_REQUEST}"
http_request POST "${IDENTITY_BASE_URL}/api/v1/auth/reissue" "${REISSUE_REQUEST}"
expect_http_status '200'
expect_base_response true 'SUCCESS'
ROTATED_ACCESS_TOKEN="$(extract_required_string \
    "${HTTP_BODY}" '.result.accessToken' '재발급 Access Token')"
ROTATED_REFRESH_TOKEN="$(extract_required_string \
    "${HTTP_BODY}" '.result.refreshToken' '재발급 Refresh Token')"
if [[ "${ROTATED_REFRESH_TOKEN}" == "${FIRST_REFRESH_TOKEN}" ]]; then
    fail 'Rotation 전후 Refresh Token이 같습니다.'
fi
FIRST_CLEANUP_REFRESH_TOKEN="${ROTATED_REFRESH_TOKEN}"

OLD_REUSE_REQUEST="${TEMP_DIR}/old-refresh-reuse.json"
write_refresh_request "${FIRST_REFRESH_TOKEN}" "${OLD_REUSE_REQUEST}"
http_request POST "${IDENTITY_BASE_URL}/api/v1/auth/reissue" "${OLD_REUSE_REQUEST}"
expect_refresh_failure 'REFRESH_TOKEN_REUSE_DETECTED'

ROTATED_AUTH_HEADER="${TEMP_DIR}/rotated-auth.header"
write_bearer_header "${ROTATED_ACCESS_TOKEN}" "${ROTATED_AUTH_HEADER}"
http_request GET "${IDENTITY_BASE_URL}/api/v1/users/me" '' "${ROTATED_AUTH_HEADER}"
expect_http_status '200'
expect_base_response true 'SUCCESS'
if ! jq -e --arg userId "${FIRST_USER_ID}" \
    '.result.userId == $userId' "${HTTP_BODY}" >/dev/null 2>&1; then
    fail '재발급 Access Token의 users/me 사용자가 다릅니다.'
fi
log '[PASS] Refresh Token이 Rotation됐고 기존 Token 재사용이 탐지됐으며 새 Access Token은 유효합니다.'

start_step '단일 로그아웃과 재발급 차단'
LOGOUT_REQUEST="${TEMP_DIR}/logout.json"
write_refresh_request "${ROTATED_REFRESH_TOKEN}" "${LOGOUT_REQUEST}"
http_request POST "${IDENTITY_BASE_URL}/api/v1/auth/logout" "${LOGOUT_REQUEST}"
expect_http_status '200'
expect_base_response true 'SUCCESS'

LOGGED_OUT_REISSUE_REQUEST="${TEMP_DIR}/logged-out-reissue.json"
write_refresh_request "${ROTATED_REFRESH_TOKEN}" "${LOGGED_OUT_REISSUE_REQUEST}"
http_request POST \
    "${IDENTITY_BASE_URL}/api/v1/auth/reissue" \
    "${LOGGED_OUT_REISSUE_REQUEST}"
expect_refresh_failure 'INVALID_REFRESH_TOKEN'
FIRST_CLEANUP_REFRESH_TOKEN=""
log '[PASS] 단일 로그아웃 후 같은 Refresh Token의 재발급이 차단됐습니다.'

start_step '전체 로그아웃과 모든 활성 Session 재발급 차단'
LOGOUT_ALL_LOGIN_REQUEST="${TEMP_DIR}/logout-all-login.json"
write_login_request "${FIRST_EMAIL}" "${LOGOUT_ALL_LOGIN_REQUEST}"
http_request POST \
    "${IDENTITY_BASE_URL}/api/v1/auth/login" \
    "${LOGOUT_ALL_LOGIN_REQUEST}"
expect_http_status '200'
expect_base_response true 'SUCCESS'
LOGOUT_ALL_ACCESS_TOKEN="$(extract_required_string \
    "${HTTP_BODY}" '.result.accessToken' '전체 로그아웃 Access Token')"
LOGOUT_ALL_REFRESH_TOKEN="$(extract_required_string \
    "${HTTP_BODY}" '.result.refreshToken' '전체 로그아웃 Refresh Token')"
FIRST_CLEANUP_REFRESH_TOKEN="${LOGOUT_ALL_REFRESH_TOKEN}"
LOGOUT_ALL_AUTH_HEADER="${TEMP_DIR}/logout-all-auth.header"
write_bearer_header "${LOGOUT_ALL_ACCESS_TOKEN}" "${LOGOUT_ALL_AUTH_HEADER}"

http_request POST \
    "${IDENTITY_BASE_URL}/api/v1/auth/logout-all" \
    '' "${LOGOUT_ALL_AUTH_HEADER}"
expect_http_status '200'
expect_base_response true 'SUCCESS'

LOGOUT_ALL_REISSUE_REQUEST="${TEMP_DIR}/logout-all-reissue.json"
write_refresh_request "${LOGOUT_ALL_REFRESH_TOKEN}" "${LOGOUT_ALL_REISSUE_REQUEST}"
http_request POST \
    "${IDENTITY_BASE_URL}/api/v1/auth/reissue" \
    "${LOGOUT_ALL_REISSUE_REQUEST}"
expect_refresh_failure 'INVALID_REFRESH_TOKEN'
FIRST_CLEANUP_REFRESH_TOKEN=""
log '[PASS] logout-all 후 해당 사용자의 활성 Refresh Token 재발급이 차단됐습니다.'

start_step '사용자 JWT 없는 Feedback Callback 공개 경로 확인'
CALLBACK_REQUEST="${TEMP_DIR}/callback.json"
jq -n --arg examId "e2e-missing-${RUN_TAG}" \
    '{user_id: $examId}' >"${CALLBACK_REQUEST}"
http_request POST \
    "${LEARNING_CORE_BASE_URL}/api/v1/exams/callback/feedback" \
    "${CALLBACK_REQUEST}"
if [[ "${HTTP_STATUS}" == '401' ]]; then
    fail 'Feedback Callback이 사용자 JWT 없음으로 401을 반환했습니다.'
fi
if [[ "${HTTP_STATUS}" != '400' && "${HTTP_STATUS}" != '404' ]]; then
    fail '존재하지 않는 examId Callback은 안전한 400 또는 404여야 합니다.'
fi
if ! jq -e '
    type == "object"
    and .isSuccess == false
    and (.code | type == "string" and length > 0)
' "${HTTP_BODY}" >/dev/null 2>&1; then
    fail 'Callback Validation/도메인 오류가 BaseResponse 형식이 아닙니다.'
fi
log '[PASS] Feedback Callback은 사용자 JWT 없이 도달했고 존재하지 않는 examId로 결과를 생성하지 않았습니다.'

if [[ "${KEEP_TEST_DATA}" == 'false' ]]; then
    start_step '남아 있는 두 번째 사용자 Refresh Session 정리'
    SECOND_LOGOUT_REQUEST="${TEMP_DIR}/second-logout.json"
    write_refresh_request "${SECOND_REFRESH_TOKEN}" "${SECOND_LOGOUT_REQUEST}"
    http_request POST "${IDENTITY_BASE_URL}/api/v1/auth/logout" "${SECOND_LOGOUT_REQUEST}"
    expect_http_status '200'
    expect_base_response true 'SUCCESS'
    SECOND_CLEANUP_REFRESH_TOKEN=""
    log '[PASS] 시나리오에 필요하지 않은 활성 Refresh Session을 폐기했습니다.'
else
    log '[INFO] E2E_KEEP_TEST_DATA=true: 추가 Refresh Session 정리를 건너뜁니다.'
    log "[INFO] 수동 확인용 examId: ${EXAM_ID}"
    log "[INFO] 수동 확인용 기대 userId: ${FIRST_USER_ID}"
fi

log ''
log '[PASS] Identity–Learning Core JWT E2E 인증 시나리오가 모두 성공했습니다.'
log '[INFO] Token 원문과 전체 Token/URL 응답은 출력하지 않았습니다.'
log '[INFO] 계정과 시험 문서는 삭제 API가 없어 로컬 테스트 DB에 남습니다. README의 정리 정책을 확인하세요.'
