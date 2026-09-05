package web.tosunsaeng.domain.usermerge.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMergedPropertiesTest {

    @Test
    void consumerRequiresWriterAndSourceDenyGate() {
        UserMergedProperties properties = new UserMergedProperties();
        properties.setConsumerEnabled(true);

        assertThatThrownBy(() -> properties.validate(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires writer and source deny gate");
    }

    @Test
    void disabledFeatureDoesNotRequireWorkloadEndpoints() {
        new UserMergedProperties().validate(true);
    }
}
