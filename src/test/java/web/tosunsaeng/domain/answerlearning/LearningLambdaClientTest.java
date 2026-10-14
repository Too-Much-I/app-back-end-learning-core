package web.tosunsaeng.domain.answerlearning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.*;
import java.io.ByteArrayInputStream;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

class LearningLambdaClientTest {
    @Test void signsWithIamAndSeoulLambdaServiceWithoutAppOrProviderSecrets() throws Exception {
        var http=mock(HttpClient.class); var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(202);
        when(response.body()).thenReturn(new ByteArrayInputStream("{\"isSuccess\":true,\"result\":{\"status\":\"queued\"}}".getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client=new LearningLambdaClient(config(),StaticCredentialsProvider.create(AwsSessionCredentials.create("test-access","test-secret","test-session")),new ObjectMapper(),http);
        assertThat(client.submit(submission()).getString("status")).isEqualTo("queued");
        var c=org.mockito.ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(c.capture(),any(HttpResponse.BodyHandler.class));
        var request=c.getValue();
        assertThat(request.headers().firstValue("Authorization").orElseThrow()).startsWith("AWS4-HMAC-SHA256").contains("/ap-northeast-2/lambda/aws4_request");
        assertThat(request.headers().firstValue("X-Amz-Security-Token")).contains("test-session");
        assertThat(request.headers().firstValue("X-Amz-Content-Sha256").orElseThrow()).hasSize(64);
        assertThat(LearningLambdaClient.payload(submission())).doesNotContainKeys("userId","apiKey","model");
    }
    @Test void configurationCannotRedirectSignedRequestsToAnotherHostOrRegion() {
        var p=config(); p.validate(); p.setLambdaUrl("https://evil.example.test/");
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        p=config(); p.setRegion("us-east-1"); assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
    @Test void cleanupWorksWithGradingDisabledButRequiresPositiveMatchingProof() throws Exception {
        var http=mock(HttpClient.class); var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(("{\"isSuccess\":true,\"result\":{\"submissionId\":\""+ID+"\",\"purged\":true}}").getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var p=config(); p.setGradingEnabled(false);
        var client=new LearningLambdaClient(p,StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access","test-secret")),new ObjectMapper(),http);
        client.purge(ID);
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().method()).isEqualTo("DELETE");
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        assertThatThrownBy(()->client.purge(ID)).hasMessage("GRADING_JOB_NOT_FOUND");
    }

}
