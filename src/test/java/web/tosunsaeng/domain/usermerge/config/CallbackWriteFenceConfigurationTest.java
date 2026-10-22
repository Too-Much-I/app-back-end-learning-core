package web.tosunsaeng.domain.usermerge.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.env.MockEnvironment;
import web.tosunsaeng.domain.usermerge.application.CallbackWriteFence;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CallbackWriteFenceConfigurationTest {
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final CallbackWriteFenceConfiguration configuration = new CallbackWriteFenceConfiguration();
    @Test void isolationCannotStartWithoutLifecycleBridge() {
        var env = new MockEnvironment().withProperty("app.callback-write-fences.isolation-enabled", "true")
                .withProperty("app.user-merged.writer-enabled", "true");
        assertThatThrownBy(() -> configuration.callbackWriteFence(mongo, env)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mongo);
    }
    @Test void lifecycleCannotStartWithoutTransactionalWriter() {
        assertThatThrownBy(() -> configuration.callbackWriteFence(mongo,
                new MockEnvironment().withProperty("app.callback-write-fences.lifecycle-enabled", "true")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mongo);
    }
    @Test void prepareStageDoesNotEnterIsolatedCallbackScope() {
        when(mongo.collectionExists(CallbackWriteFence.COLLECTION)).thenReturn(true);
        var fence = configuration.callbackWriteFence(mongo, new MockEnvironment()
                .withProperty("app.callback-write-fences.lifecycle-enabled", "true")
                .withProperty("app.user-merged.writer-enabled", "true"));
        assertThat(fence.within("owner", "exam", CallbackWriteFence.Lane.AZURE, fence::scoped)).isFalse();
    }
    @Test void activationScopesCallbackAndAlwaysClearsIt() {
        when(mongo.collectionExists(CallbackWriteFence.COLLECTION)).thenReturn(true);
        var fence = configuration.callbackWriteFence(mongo, new MockEnvironment()
                .withProperty("app.callback-write-fences.lifecycle-enabled", "true")
                .withProperty("app.callback-write-fences.isolation-enabled", "true")
                .withProperty("app.user-merged.writer-enabled", "true"));
        assertThat(fence.within("owner", "exam", CallbackWriteFence.Lane.AZURE, fence::scoped)).isTrue();
        assertThat(fence.scoped()).isFalse();
    }
}
