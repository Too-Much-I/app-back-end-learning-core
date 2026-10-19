package web.tosunsaeng.domain.learningrecorddeletion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import web.tosunsaeng.domain.learningrecorddeletion.domain.BillingDeletionDelivery;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.BillingDeletionClient;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BillingDeletionDeliveryTest {
    static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    BillingDeletionDelivery delivery() {
        return BillingDeletionDelivery.create("test", "00000000-0000-0000-0000-000000000001",
                "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222", "session", NOW, NOW);
    }
    @Test void immutableRequestAndIndependentEvent() {
        var d = delivery(); String body = d.getPayload();
        assertThat(d.claim(NOW, "lease")).isTrue(); d.retry(NOW, null, 0);
        assertThat(d.getPayload()).isEqualTo(body);
        assertThat(delivery().getEventId()).isNotEqualTo(d.getEventId());
        assertThat(body).contains("\"deletedAt\":\"2026-10-16T00:00:00.000Z\"");
    }
    @Test void floorSurvivesInvalidAndEarlierHeaders() {
        var d = delivery(); d.claim(NOW,"a"); d.retry(NOW, NOW.plusSeconds(100),0);
        assertThat(d.claim(NOW.plusSeconds(99),"b")).isFalse();
        d.claim(NOW.plusSeconds(100),"b"); d.retry(NOW.plusSeconds(100),null,0);
        assertThat(d.getRetryNotBefore()).isEqualTo(NOW.plusSeconds(100));
        assertThat(d.getNextAttemptAt()).isAfter(NOW.plusSeconds(100));
    }
    @Test void exactDeadlineNoSendButInFlightSuccessCanBeSaved() {
        var d = delivery(); d.claim(NOW,"a");
        d.retry(NOW,null,0);
        Instant last = NOW.plusSeconds(86400).minusMillis(1);
        assertThat(d.claim(last,"b")).isTrue();
        assertThat(d.maySend("b",last)).isTrue();
        assertThat(d.maySend("b",NOW.plusSeconds(86400))).isFalse();
        assertThat(d.owned("b",NOW.plusSeconds(86400))).isTrue();
        d.acknowledge(NOW.plusSeconds(86400));
        assertThat(d.getState()).isEqualTo(BillingDeletionDelivery.State.ACKED);
    }
    @Test void twoHundredthSuccessAndNoTwoHundredFirst() {
        var d = delivery(); Instant time = NOW;
        for (int i=1;i<=200;i++) {
            assertThat(d.claim(time,"t")).isTrue();
            if (i < 200) { d.retry(time,null,0); time = d.getNextAttemptAt(); }
        }
        assertThat(d.maySend("t",time)).isTrue(); d.acknowledge(time);
        assertThat(d.claim(time.plusSeconds(31),"x")).isFalse();
        assertThat(d.getAttempts()).isEqualTo(200);
    }
    @Test void floorAtDeadlineRequiresReviewWithoutAutomaticDue() {
        var d = delivery(); d.claim(NOW,"a"); d.retry(NOW,NOW.plusSeconds(86400),0);
        assertThat(d.getFailureCode()).isEqualTo(BillingDeletionDelivery.Failure.RETRY_AFTER_REVIEW);
        assertThat(d.getNextAttemptAt()).isNull(); assertThat(d.getLeaseToken()).isNull();
    }
    @ParameterizedTest @ValueSource(ints={200,202,301,400,404,405,409,413,415,422})
    void non204NeverAcknowledged(int code) {
        assertThat(BillingDeletionClient.classify(code,Map.of(),new byte[0],NOW).acknowledged()).isFalse();
        assertThat(BillingDeletionClient.classify(code,Map.of(),new byte[0],NOW).failure()).isNotNull();
    }
    @ParameterizedTest @ValueSource(ints={408,429,500,502,503,504})
    void transientDoesNotBecomeAcknowledgment(int code) {
        var result = BillingDeletionClient.classify(code,Map.of(),"<html>".getBytes(StandardCharsets.UTF_8),NOW);
        assertThat(result.acknowledged()).isFalse(); assertThat(result.failure()).isNull();
    }
    @Test void framingAndAuthPriority() {
        assertThat(BillingDeletionClient.classify(204,Map.of(),new byte[0],NOW).acknowledged()).isTrue();
        assertThat(BillingDeletionClient.classify(204,Map.of(),new byte[1],NOW).failure()).isEqualTo(BillingDeletionDelivery.Failure.CONTRACT_ERROR);
        assertThat(BillingDeletionClient.classify(204,Map.of("Content-Length",List.of("1")),new byte[0],NOW).failure()).isEqualTo(BillingDeletionDelivery.Failure.CONTRACT_ERROR);
        assertThat(BillingDeletionClient.classify(503,Map.of(),new byte[16385],NOW).failure()).isEqualTo(BillingDeletionDelivery.Failure.CONTRACT_ERROR);
        assertThat(BillingDeletionClient.classify(401,Map.of(),new byte[0],NOW).failure()).isEqualTo(BillingDeletionDelivery.Failure.AUTHENTICATION_FAILED);
    }
    @Test void strict503AndOpaqueTrace() {
        String body = "{\"code\":\"BILLING_TEMPORARILY_UNAVAILABLE\",\"message\":\"Billing is temporarily unavailable.\",\"retryable\":true,\"correlationId\":\"fake-trace_01.test\"}";
        assertThat(BillingDeletionClient.classify(503,Map.of(),body.getBytes(StandardCharsets.UTF_8),NOW).failure()).isNull();
        assertThat(BillingDeletionClient.classify(503,Map.of(),body.replace("true","false").getBytes(StandardCharsets.UTF_8),NOW).failure()).isNotNull();
        assertThat(BillingDeletionClient.classify(503,Map.of(),"[]".getBytes(StandardCharsets.UTF_8),NOW).failure()).isNotNull();
    }
    @Test void invalidRetryHeadersNeverWrap() {
        for (var values : List.of(List.of("1","2"),List.of("-1"),List.of("999999999999999999999999")))
            assertThat(BillingDeletionClient.retryAfter(Map.of("Retry-After",values),NOW)).isNull();
        assertThat(BillingDeletionClient.retryAfter(Map.of("Retry-After",List.of("0")),NOW)).isEqualTo(NOW);
    }
    @Test void duplicateAndTrailing503AreContractFailures() {
        for (String json : List.of("{\"x\":1,\"x\":1}", "{} {}", "null", "123"))
            assertThat(BillingDeletionClient.classify(503,Map.of(),json.getBytes(StandardCharsets.UTF_8),NOW).failure())
                    .isEqualTo(BillingDeletionDelivery.Failure.CONTRACT_ERROR);
    }
    @Test void lastFailedAttemptAndCrashBudgetCannotReset() {
        var d = delivery(); Instant time = NOW;
        for(int i=0;i<200;i++) {
            assertThat(d.claim(time,"x")).isTrue(); d.retry(time,null,0);
            if (i<199) time = d.getNextAttemptAt();
        }
        assertThat(d.getFailureCode()).isEqualTo(BillingDeletionDelivery.Failure.RETRY_EXHAUSTED);
        assertThat(d.claim(NOW.plus(Duration.ofDays(2)),"new")).isFalse();
        assertThat(d.getFirstAttemptAt()).isEqualTo(NOW);
    }
}
