package web.tosunsaeng.domain.exams.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import static web.tosunsaeng.domain.exams.billing.BillingClientException.Category.*;

/** Strict new paid error envelope. Never stores/logs the body, message or correlationId. */
public final class PaidBillingErrorDecoder {
    public enum Endpoint { RESERVE, STATUS, CONFIRM, CANCEL, PHONE_DISCOVERY }
    private final ObjectMapper mapper;
    public PaidBillingErrorDecoder(ObjectMapper mapper) { this.mapper = mapper; }

    public BillingClientException decode(Endpoint endpoint, int http, byte[] bytes, String retryAfter) {
        if (http == 401 || http == 403) return error(AUTH_FAILURE, null);
        try {
            check(bytes != null && bytes.length > 0 && bytes.length <= 16384);
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode root = mapper.readTree(json);
            check(root != null && root.isObject() && root.size() == 4);
            for (String field : Set.of("code", "message", "retryable", "correlationId")) check(root.hasNonNull(field));
            String code = text(root, "code"), message = text(root, "message");
            text(root, "correlationId");
            check(root.get("retryable").isBoolean());
            boolean retry = root.get("retryable").booleanValue();
            boolean reserveOrStatus = endpoint == Endpoint.RESERVE || endpoint == Endpoint.STATUS;
            return switch (code) {
                case "MEMBER_EVIDENCE_PENDING" -> {
                    check(reserveOrStatus && http == 503 && retry && "1".equals(retryAfter));
                    check(message.equals("The operation is waiting for member evidence and request resumption."));
                    yield error(MEMBER_EVIDENCE_PENDING, 1);
                }
                case "RESERVE_REJECTED_BEFORE_CREATION" -> {
                    check(endpoint == Endpoint.STATUS && http == 409 && !retry && retryAfter == null);
                    check(message.equals("The reserve operation was rejected before reservation creation."));
                    yield error(PRE_RESERVE_REJECTED, null);
                }
                case "OPERATION_OWNER_MISMATCH" -> {
                    check(http == 409 && !retry && retryAfter == null);
                    check(message.equals("The operation owner does not match the current owner."));
                    yield error(OWNER_BLOCKED, null);
                }
                case "OPERATION_NOT_FOUND" -> {
                    check(endpoint == Endpoint.STATUS && http == 404 && !retry && retryAfter == null);
                    yield error(OPERATION_NOT_FOUND, null);
                }
                case "BILLING_TEMPORARILY_UNAVAILABLE" -> {
                    check(http == 503 && retry && "1".equals(retryAfter));
                    yield error(TEMPORARILY_UNAVAILABLE, 1);
                }
                case "TRANSITION_CONTEXT_CONFLICT" -> {
                    check(endpoint == Endpoint.RESERVE && http == 409 && !retry && retryAfter == null);
                    yield error(TRANSITION_CONTEXT_CONFLICT, null);
                }
                case "ENTITLEMENT_REVOKED" -> {
                    check(endpoint == Endpoint.CONFIRM && http == 409 && !retry && retryAfter == null);
                    yield error(ENTITLEMENT_REVOKED, null);
                }
                case "INVALID_REQUEST", "INVALID_IDEMPOTENCY_KEY" -> {
                    check(http == 400 && !retry && retryAfter == null);
                    yield error(INVALID_REQUEST, null);
                }
                case "ENTITLEMENT_INSUFFICIENT" -> {
                    check(endpoint == Endpoint.RESERVE && http == 402 && !retry && retryAfter == null);
                    yield error(ENTITLEMENT_INSUFFICIENT, null);
                }
                case "IDEMPOTENCY_KEY_CONFLICT" -> {
                    check(endpoint != Endpoint.STATUS && endpoint != Endpoint.PHONE_DISCOVERY && http == 409 && !retry && retryAfter == null);
                    yield error(IDEMPOTENCY_CONFLICT, null);
                }
                case "RESERVATION_STATE_CONFLICT" -> {
                    check((endpoint == Endpoint.CONFIRM || endpoint == Endpoint.CANCEL) && http == 409 && !retry && retryAfter == null);
                    yield error(RESERVATION_CONFLICT, null);
                }
                case "COMMAND_PROCESSING" -> {
                    check(endpoint != Endpoint.STATUS && http == 409 && retry);
                    yield error(PROCESSING, positiveRetry(retryAfter));
                }
                case "RATE_LIMITED" -> {
                    check(http == 429 && retry);
                    yield error(RATE_LIMITED, positiveRetry(retryAfter));
                }
                default -> error(CONTRACT_ERROR, null);
            };
        } catch (Exception invalid) {
            // Do not attach parser exceptions carrying response fragments.
            return error(CONTRACT_ERROR, null);
        }
    }
    private static int positiveRetry(String value) {
        check(value != null && value.matches("[1-9][0-9]{0,2}"));
        int seconds = Integer.parseInt(value); check(seconds <= 300); return seconds;
    }
    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field); check(node.isTextual());
        String value = node.textValue(); check(!value.isBlank());
        for (int i=0; i<value.length(); i++) {
            char c=value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                check(i+1<value.length() && Character.isLowSurrogate(value.charAt(++i)));
            } else check(!Character.isLowSurrogate(c));
        }
        return value;
    }
    private static void check(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid paid error contract"); }
    private static BillingClientException error(BillingClientException.Category category, Integer retry) {
        return new BillingClientException(category, retry);
    }
}
