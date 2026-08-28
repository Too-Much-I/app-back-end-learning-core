package web.tosunsaeng.global.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        log.warn(
                "인증되지 않은 요청 거절 event=http.security outcome=rejected "
                        + "reason=unauthorized method={} path={} "
                        + "errorCode={} errorType={}",
                request.getMethod(),
                request.getRequestURI(),
                ErrorStatus._UNAUTHORIZED.getCode(),
                authenticationException.getClass().getName()
        );
        writeErrorResponse(response, ErrorStatus._UNAUTHORIZED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        log.warn(
                "접근 권한이 없는 요청 거절 event=http.security outcome=rejected "
                        + "reason=forbidden method={} path={} "
                        + "errorCode={} errorType={}",
                request.getMethod(),
                request.getRequestURI(),
                ErrorStatus._FORBIDDEN.getCode(),
                accessDeniedException.getClass().getName()
        );
        writeErrorResponse(response, ErrorStatus._FORBIDDEN);
    }

    public void writeErrorResponse(HttpServletResponse response, ErrorStatus errorStatus) throws IOException {
        response.setStatus(errorStatus.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), BaseResponse.onFailure(errorStatus, null));
    }
}
