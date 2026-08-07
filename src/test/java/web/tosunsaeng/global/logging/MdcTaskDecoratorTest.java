package web.tosunsaeng.global.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void callerContextIsAppliedAndWorkerContextIsRestored() {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> requestIdInsideTask = new AtomicReference<>();
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "caller-request-id");
        Runnable decorated = decorator.decorate(() -> requestIdInsideTask.set(
                MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)
        ));

        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "worker-request-id");
        decorated.run();

        assertEquals("caller-request-id", requestIdInsideTask.get());
        assertEquals(
                "worker-request-id",
                MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)
        );
    }
}
