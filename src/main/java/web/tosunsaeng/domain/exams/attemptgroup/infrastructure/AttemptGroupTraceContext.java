package web.tosunsaeng.domain.exams.attemptgroup.infrastructure;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class AttemptGroupTraceContext {
    private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ZERO_SPAN_ID = "0000000000000000";

    private final ObjectProvider<Tracer> tracerProvider;
    private final SecureRandom random = new SecureRandom();

    public AttemptGroupTraceContext(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    public StoredContext captureOrCreate() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer != null) {
            Span span = tracer.currentSpan();
            if (span != null && valid(span.context().traceId(), span.context().spanId())) {
                return new StoredContext(
                        span.context().traceId(),
                        span.context().spanId(),
                        Boolean.TRUE.equals(span.context().sampled()) ? "01" : "00",
                        false
                );
            }
        }
        return new StoredContext(randomHex(16), randomHex(8), "01", true);
    }

    public TraceContext restore(Tracer tracer, StoredContext stored) {
        if (tracer == null || stored == null || !valid(stored.traceId(), stored.parentSpanId())) {
            return null;
        }
        return tracer.traceContextBuilder()
                .traceId(stored.traceId())
                .spanId(stored.parentSpanId())
                .sampled("01".equals(stored.traceFlags()))
                .build();
    }

    public boolean valid(String traceId, String spanId) {
        return traceId != null && traceId.matches("[0-9a-f]{32}")
                && !ZERO_TRACE_ID.equals(traceId)
                && spanId != null && spanId.matches("[0-9a-f]{16}")
                && !ZERO_SPAN_ID.equals(spanId);
    }

    private String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    public record StoredContext(
            String traceId,
            String parentSpanId,
            String traceFlags,
            boolean fallback
    ) {
    }
}
