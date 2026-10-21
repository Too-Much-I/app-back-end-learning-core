package web.tosunsaeng.domain.exams.rollout;

import org.junit.jupiter.api.Test;
import java.util.*;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import static org.assertj.core.api.Assertions.*;

class LegacyExamManifestTest {
    @Test void productionToolReplicaSetFixtureMatchesJavaDigest() {
        var manifest = LegacyExamManifest.build("production", "to-teacher-app", "lc-local-rehearsal-r1",
                List.of("fake-exam-한글", "fake-exam-a"));
        assertThat(manifest.summary().sha256())
                .isEqualTo("ce6c343a0d26f685a9f84054dcc498a69b80dbb6bcfeeede857e39b81c16da8a");
    }
    @Test void canonicalDigestIsOrderIndependentAndContextBound() {
        var a = LegacyExamManifest.build("test", "fixture", "r1", List.of("ex_b", "ex_a"));
        // Independently computed with Node crypto using the documented length-prefixed UTF-8 format.
        assertThat(a.summary().sha256()).isEqualTo("4254516a8cf345404ac1cde504090ade1327c227b60b096d4645116dbda2da6c");
        assertThat(a.summary()).isEqualTo(LegacyExamManifest.build("test", "fixture", "r1", List.of("ex_a", "ex_b")).summary());
        for (var other : List.of(
                LegacyExamManifest.build("staging", "fixture", "r1", List.of("ex_b", "ex_a")),
                LegacyExamManifest.build("test", "another", "r1", List.of("ex_b", "ex_a")),
                LegacyExamManifest.build("test", "fixture", "r2", List.of("ex_b", "ex_a")),
                LegacyExamManifest.build("test", "fixture", "r1", List.of("ex_a"))))
            assertThat(other.summary().sha256()).isNotEqualTo(a.summary().sha256());
        assertThat(a.toString()).doesNotContain("ex_a", "ex_b", "fixture", "environment", "database");
    }

    @Test void malformedDuplicateOrOversizedInventoryFailsWithoutIdentifiers() {
        for (var ids : List.of(List.of("secret-id", "secret-id"), List.of(""), List.of("\ud800"),
                List.of("a\nb"), List.of("a".repeat(129))))
            assertThatThrownBy(() -> LegacyExamManifest.build("test", "fixture", "r1", ids))
                    .hasMessage("Legacy manifest verification failed").hasNoCause();
        assertThatThrownBy(() -> LegacyExamManifest.build("test", "fixture", "r1",
                Collections.nCopies(LegacyExamManifest.MAX_ENTRIES + 1, "id"))).isInstanceOf(IllegalStateException.class);
    }

    @Test void exactMembershipIsNotSourceFallbackOrPermission() {
        var manifest = LegacyExamManifest.build("test", "fixture", "r1", List.of("legacy"));
        assertThat(manifest.containsLegacy(ExamSession.builder().examId("legacy").active(false).accessRevoked(true).build())).isTrue();
        assertThat(manifest.containsLegacy(ExamSession.builder().examId("new-no-source").build())).isFalse();
        assertThat(manifest.containsLegacy(null)).isFalse();
        assertThatThrownBy(() -> manifest.containsLegacy(ExamSession.builder().examId("legacy")
                .billingReservationId("reservation").build())).isInstanceOf(IllegalStateException.class);
    }

    @Test void invalidEnvironmentDatabaseAndRevisionFailClosed() {
        assertThatThrownBy(() -> LegacyExamManifest.build("prod", "fixture", "r1", List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LegacyExamManifest.build("test", "admin", "r1", List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LegacyExamManifest.build("test", "fixture", "", List.of())).isInstanceOf(IllegalStateException.class);
    }
}
