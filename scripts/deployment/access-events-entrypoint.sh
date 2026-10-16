#!/bin/sh
# No secret values in arguments/logs. Java validates the PKCS12 key/alias/certificate.
set -eu
fail() { printf '%s\n' 'LC access TLS material preparation failed' >&2; exit 1; }

case "${BILLING_ACCESS_EVENTS_RECEIVER_ENABLED:-false}" in
  false) unset BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64; exec "$@" ;;
  true) ;;
  *) fail ;;
esac

# Preserve the existing externally mounted keystore contract.
if [ -z "${BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64:-}" ]; then
  case "${BILLING_ACCESS_EVENTS_TLS_KEY_STORE:-}" in /*) exec "$@" ;; *) fail ;; esac
fi
[ -z "${BILLING_ACCESS_EVENTS_TLS_KEY_STORE:-}" ] || fail
[ -n "${BILLING_ACCESS_EVENTS_TLS_KEY_STORE_PASSWORD:-}" ] || fail
[ -n "${BILLING_ACCESS_EVENTS_TLS_KEY_ALIAS:-}" ] || fail
[ "${#BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64}" -le 65536 ] || fail

umask 077
tls_dir=$(mktemp -d /tmp/lc-access-events.XXXXXXXX) || fail
tls_file="$tls_dir/receiver.p12"
cleanup() {
  # Only this invocation's exact file and empty directory, never recursive removal.
  rm -f -- "$tls_file"
  rmdir -- "$tls_dir"
}
trap cleanup EXIT
if ! printf '%s' "$BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64" | base64 --decode > "$tls_file" 2>/dev/null; then
  fail
fi
unset BILLING_ACCESS_EVENTS_TLS_PKCS12_BASE64
[ -s "$tls_file" ] || fail
tls_size=$(wc -c < "$tls_file")
[ "$tls_size" -le 49152 ] || fail
chmod 600 "$tls_file" || fail
export BILLING_ACCESS_EVENTS_TLS_KEY_STORE="$tls_file"
# exec preserves PID1/signals. The private file lives only in the task filesystem.
exec "$@"
