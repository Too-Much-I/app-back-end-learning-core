package web.tosunsaeng.domain.answerlearning;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** One bounded INFO record; correlation ID is supplied by the outer RequestCorrelationFilter. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LearningTimingFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/api/v1/learning";

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals(PREFIX) && !path.startsWith(PREFIX + "/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
        var timing = LearningRequestTiming.begin();
        long started = System.nanoTime();
        boolean failed = false;
        try { chain.doFilter(request, response); }
        catch (IOException | ServletException | RuntimeException | Error exception) {
            failed = true;
            throw exception;
        } finally {
            LearningRequestTiming.clear();
            long total = TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - started));
            log.info("event=learning.request.timing route={} status={} totalMs={} mongoCommandMs={} mongoCommands={} billingMs={} billingCalls={} outcome={}",
                    route(request), failed ? 500 : response.getStatus(), total,
                    timing.mongoCommandMs(), timing.mongoCommands(), timing.billingMs(), timing.billingCalls(),
                    failed ? "exception" : "completed");
        }
    }

    // Never log raw URI, query string, method, IDs, payload or exception text.
    static String route(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String suffix = path.substring(PREFIX.length());
        return switch (suffix) {
            case "/units" -> "units";
            case "/resume" -> "resume";
            case "/capabilities" -> "capabilities";
            case "/submissions" -> "submissions";
            default -> {
                if (suffix.matches("/units/[^/]+")) yield "unit_detail";
                if (suffix.matches("/media/[^/]+/url")) yield "media_url";
                if (suffix.matches("/lessons/[^/]+/completion")) yield "completion";
                if (suffix.matches("/submissions/[^/]+")) yield "submission_status";
                if (suffix.matches("/submissions/[^/]+/upload-url")) yield "upload_url";
                if (suffix.matches("/submissions/[^/]+/submit")) yield "submit";
                if (suffix.matches("/submissions/[^/]+/retry")) yield "retry";
                yield "other";
            }
        };
    }
}
