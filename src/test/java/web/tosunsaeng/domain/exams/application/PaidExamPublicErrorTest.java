package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.exception.GlobalExceptionAdvice;
import web.tosunsaeng.global.sentry.UnexpectedExceptionReporter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Actual public exception/serialization boundary; does not claim the paid route is enabled. */
class PaidExamPublicErrorTest {
    final GlobalExceptionAdvice advice=new GlobalExceptionAdvice(mock(UnexpectedExceptionReporter.class));
    final ObjectMapper json=new ObjectMapper();

    void check(ErrorStatus error,Integer retry,int status,String message) throws Exception {
        var response=advice.onThrowException(new ExamsException(error,retry),new MockHttpServletRequest("POST","/api/v1/exams"));
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo(retry==null?null:retry.toString());
        var body=json.readTree(json.writeValueAsBytes(response.getBody()));
        assertThat(body.size()).isEqualTo(3);
        assertThat(body.get("isSuccess").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo(error.getCode());
        assertThat(body.get("message").asText()).isEqualTo(message);
    }
    @Test void normalCreationConflictKeepsItsOwnMessage() throws Exception {
        check(ErrorStatus._EXAM_CREATION_PROCESSING,1,409,"시험 생성이 처리 중입니다. 같은 요청으로 다시 시도해 주세요.");
    }
    @Test void memberPendingKeepsExistingTemporaryResponse() throws Exception {
        check(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE,1,503,"시험 사용권을 일시적으로 확인할 수 없습니다.");
    }
    @Test void deletedSuccessHasNoRetryHeaderOrResult() throws Exception {
        check(ErrorStatus._EXAM_NOT_FOUND,null,404,"해당 모의고사 세션을 찾을 수 없습니다.");
    }
}
