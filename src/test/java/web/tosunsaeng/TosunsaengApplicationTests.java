package web.tosunsaeng;

import io.sentry.SentryOptions;
import io.sentry.spring.jakarta.SentryExceptionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import web.tosunsaeng.global.sentry.SanitizedSentryExceptionResolver;
import web.tosunsaeng.global.sentry.SentryEventSanitizer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ActiveProfiles("test")
@SpringBootTest
class TosunsaengApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

	@Test
	void contextLoads() {
	}

    @Test
    void sentryUsesSafeErrorOnlyConfiguration() {
        SentryOptions.BeforeSendCallback beforeSendCallback = applicationContext.getBean(
                SentryOptions.BeforeSendCallback.class
        );
        SentryExceptionResolver exceptionResolver = applicationContext.getBean(
                SentryExceptionResolver.class
        );

        assertAll(
                () -> assertEquals("false", environment.getProperty("sentry.logging.enabled")),
                () -> assertEquals("none", environment.getProperty("sentry.max-request-body-size")),
                () -> assertEquals("1", environment.getProperty("sentry.exception-resolver-order")),
                () -> assertEquals("test", environment.getProperty("sentry.environment")),
                () -> assertEquals(
                        "app-back-end-learning-core@test",
                        environment.getProperty("sentry.release")
                ),
                () -> assertInstanceOf(SentryEventSanitizer.class, beforeSendCallback),
                () -> assertInstanceOf(SanitizedSentryExceptionResolver.class, exceptionResolver),
                () -> assertFalse(applicationContext.containsBean("sentryLogbackInitializer"))
        );
    }

}
