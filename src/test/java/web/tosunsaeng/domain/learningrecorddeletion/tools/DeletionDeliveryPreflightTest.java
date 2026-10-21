package web.tosunsaeng.domain.learningrecorddeletion.tools;

import com.mongodb.ConnectionString;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DeletionDeliveryPreflightTest {
    @Test void defaultsToInspectWithoutApprovalOrSigningKey() {
        assertThat(DeletionDeliveryPreflight.sendMode(Map.of())).isFalse();
    }
    @Test void sendRequiresExactApprovalAndDrain() {
        assertThatThrownBy(() -> DeletionDeliveryPreflight.sendMode(Map.of("LC_PREFLIGHT_MODE", "send-once")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeletionDeliveryPreflight.sendMode(Map.of("LC_PREFLIGHT_MODE", "send-once",
                "LC_PREFLIGHT_APPROVAL", "SEND_EXACT_APPROVED_TARGET"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(DeletionDeliveryPreflight.sendMode(Map.of("LC_PREFLIGHT_MODE", "send-once",
                "LC_PREFLIGHT_APPROVAL", "SEND_EXACT_APPROVED_TARGET", "LC_PREFLIGHT_OTHER_WRITERS_DRAINED", "true"))).isTrue();
    }
    @Test void unknownModeNeverBecomesSend() {
        assertThatThrownBy(() -> DeletionDeliveryPreflight.sendMode(Map.of("LC_PREFLIGHT_MODE", "send")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void databaseMustMatchExactProductionTarget() {
        assertThatCode(() -> DeletionDeliveryPreflight.requireProductionDatabase(
                new ConnectionString("mongodb+srv://cluster0.nuuovhj.mongodb.net/to-teacher-app"))).doesNotThrowAnyException();
        for (String uri : java.util.List.of("mongodb://localhost/to-teacher-app",
                "mongodb+srv://other.example/to-teacher-app", "mongodb+srv://cluster0.nuuovhj.mongodb.net/test"))
            assertThatThrownBy(() -> DeletionDeliveryPreflight.requireProductionDatabase(new ConnectionString(uri)))
                    .isInstanceOf(IllegalArgumentException.class);
    }
}
