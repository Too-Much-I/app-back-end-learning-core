package web.tosunsaeng.domain.usermerge.application;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.usermerge.api.UserMergedEventRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMergedEventNormalizerTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-20T02:00:01Z");

    @Test
    void normalizesSchemaV1AndUsesTheFixedSemanticDigest() {
        NormalizedUserMergedEvent normalized = UserMergedEventNormalizer.normalize(
                request(
                        "9a88bc80-d73a-4a3d-8f68-492641d27208",
                        "73a18ed4-1d56-4c4f-afd6-b39175b82a86",
                        "45c05c3f-ae7f-4ca7-af88-3ab8aa8f428e"
                ),
                RECEIVED_AT
        );

        assertThat(normalized.payloadDigest()).isEqualTo(
                "6191e7ef114abcf0ebc9ce7ac78779bf98027f714bbec2b81219ca843ed523bd"
        );
        assertThat(normalized.occurredAt()).isEqualTo("2026-08-20T02:00:00Z");
        assertThat(normalized.receivedAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void rejectsNonCanonicalUuidUnknownSchemaAndSameSourceTarget() {
        assertInvalid(new UserMergedEventRequest(
                "9A88BC80-D73A-4A3D-8F68-492641D27208",
                1,
                "73a18ed4-1d56-4c4f-afd6-b39175b82a86",
                "45c05c3f-ae7f-4ca7-af88-3ab8aa8f428e",
                "2026-08-20T02:00:00Z"
        ));
        assertInvalid(new UserMergedEventRequest(
                "9a88bc80-d73a-4a3d-8f68-492641d27208",
                2,
                "73a18ed4-1d56-4c4f-afd6-b39175b82a86",
                "45c05c3f-ae7f-4ca7-af88-3ab8aa8f428e",
                "2026-08-20T02:00:00Z"
        ));
        assertInvalid(request(
                "9a88bc80-d73a-4a3d-8f68-492641d27208",
                "73a18ed4-1d56-4c4f-afd6-b39175b82a86",
                "73a18ed4-1d56-4c4f-afd6-b39175b82a86"
        ));
    }

    private static UserMergedEventRequest request(String eventId, String source, String target) {
        return new UserMergedEventRequest(
                eventId,
                1,
                source,
                target,
                "2026-08-20T02:00:00Z"
        );
    }

    private static void assertInvalid(UserMergedEventRequest request) {
        assertThatThrownBy(() -> UserMergedEventNormalizer.normalize(request, RECEIVED_AT))
                .isInstanceOfSatisfying(UserMergedEventException.class, failure ->
                        assertThat(failure.getReason())
                                .isEqualTo(UserMergedEventException.Reason.INVALID_PAYLOAD));
    }
}
