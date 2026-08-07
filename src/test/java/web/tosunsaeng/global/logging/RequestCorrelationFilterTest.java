package web.tosunsaeng.global.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void requestIdExistsOnlyInsideFilterAndDoesNotChangeResponseContract() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/exams/ex_correlation/status"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            requestIdInsideChain.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));
            ((MockHttpServletResponse) servletResponse).setStatus(200);
        };

        filter.doFilterInternal(request, response, chain);

        assertFalse(requestIdInsideChain.get().isBlank());
        assertNull(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));
        assertNull(response.getHeader("X-Request-ID"));
    }

    @Test
    void existingMdcRequestIdIsRestoredAfterRequest() throws Exception {
        MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "outer-request-id");
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();

        filter.doFilterInternal(
                request,
                response,
                (servletRequest, servletResponse) ->
                        requestIdInsideChain.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY))
        );

        assertFalse("outer-request-id".equals(requestIdInsideChain.get()));
        assertEquals("outer-request-id", MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY));
    }
}
