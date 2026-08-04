package web.tosunsaeng.domain.notifications.provider;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import web.tosunsaeng.domain.notifications.domain.enums.NotificationErrorCode;
import web.tosunsaeng.global.config.NotificationPushProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExpoPushProviderTest {

    private static final String SEND_URL = "https://expo.example.test/send";
    private static final String RECEIPT_URL = "https://expo.example.test/receipts";

    @Test
    void senderUsesAuthorizationOnlyWhenConfiguredAndMapsTicket() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-access-token-placeholder"))
                .andExpect(jsonPath("$[0].to").value("ExpoPushToken[placeholder-value]"))
                .andExpect(jsonPath("$[0].title").value("채점이 완료됐어요"))
                .andExpect(jsonPath("$[0].data.type").value("EXAM_GRADING_COMPLETED"))
                .andRespond(withSuccess("""
                        {"data":[{"status":"ok","id":"ticket-id"}]}
                        """, MediaType.APPLICATION_JSON));

        PushTicketBatchResult result = new ExpoPushNotificationSender(
                restTemplate,
                properties("test-access-token-placeholder")
        ).send(List.of(message()));

        assertEquals("ticket-id", result.results().getFirst().ticketId());
        server.verify();
    }

    @Test
    void senderOmitsAuthorizationWhenAccessTokenIsBlank() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SEND_URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        {"data":[{"status":"ok","id":"ticket-id"}]}
                        """, MediaType.APPLICATION_JSON));

        new ExpoPushNotificationSender(restTemplate, properties(""))
                .send(List.of(message()));

        server.verify();
    }

    @Test
    void http429IsNormalizedWithoutProviderResponseBody() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("provider-response-must-not-propagate"));

        PushProviderException exception = assertThrows(
                PushProviderException.class,
                () -> new ExpoPushNotificationSender(restTemplate, properties(""))
                        .send(List.of(message()))
        );

        assertEquals(NotificationErrorCode.PROVIDER_RATE_LIMITED, exception.getErrorCode());
        assertEquals(false, exception.getMessage().contains("provider-response-must-not-propagate"));
    }

    @Test
    void http5xxAndTimeoutAreNormalizedAsRetryableErrors() {
        RestTemplate unavailableTemplate = new RestTemplate();
        MockRestServiceServer unavailableServer = MockRestServiceServer
                .bindTo(unavailableTemplate).build();
        unavailableServer.expect(requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        PushProviderException unavailable = assertThrows(
                PushProviderException.class,
                () -> new ExpoPushNotificationSender(unavailableTemplate, properties(""))
                        .send(List.of(message()))
        );
        assertEquals(NotificationErrorCode.PROVIDER_UNAVAILABLE, unavailable.getErrorCode());

        RestTemplate timeoutTemplate = new RestTemplate();
        MockRestServiceServer timeoutServer = MockRestServiceServer.bindTo(timeoutTemplate).build();
        timeoutServer.expect(requestTo(SEND_URL)).andRespond(request -> {
            throw new ResourceAccessException("synthetic timeout without a token");
        });
        PushProviderException timeout = assertThrows(
                PushProviderException.class,
                () -> new ExpoPushNotificationSender(timeoutTemplate, properties(""))
                        .send(List.of(message()))
        );
        assertEquals(NotificationErrorCode.PROVIDER_TIMEOUT, timeout.getErrorCode());
    }

    @Test
    void ticketErrorsAreNormalizedWithoutPersistingProviderMessage() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess("""
                        {
                          "data": [{
                            "status": "error",
                            "message": "provider message is intentionally ignored",
                            "details": {"error": "DeviceNotRegistered"}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        PushTicketResult result = new ExpoPushNotificationSender(restTemplate, properties(""))
                .send(List.of(message())).results().getFirst();

        assertEquals(NotificationErrorCode.DEVICE_NOT_REGISTERED, result.errorCode());
    }

    @Test
    void receiptClientDistinguishesSentAndDeviceNotRegistered() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(RECEIPT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ids[0]").value("ticket-ok"))
                .andExpect(jsonPath("$.ids[1]").value("ticket-invalid"))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "ticket-ok": {"status": "ok"},
                            "ticket-invalid": {
                              "status": "error",
                              "details": {"error": "DeviceNotRegistered"}
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PushReceiptBatchResult result = new ExpoPushReceiptClient(
                restTemplate,
                properties("")
        ).getReceipts(List.of("ticket-ok", "ticket-invalid"));

        assertEquals(true, result.results().get("ticket-ok").successful());
        assertEquals(
                NotificationErrorCode.DEVICE_NOT_REGISTERED,
                result.results().get("ticket-invalid").errorCode()
        );
        server.verify();
    }

    @Test
    void receiptMessageRateExceededIsRetryableAndInvalidCredentialsIsPermanent() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(RECEIPT_URL))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "ticket-rate": {
                              "status": "error",
                              "details": {"error": "MessageRateExceeded"}
                            },
                            "ticket-credential": {
                              "status": "error",
                              "details": {"error": "InvalidCredentials"}
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PushReceiptBatchResult result = new ExpoPushReceiptClient(restTemplate, properties(""))
                .getReceipts(List.of("ticket-rate", "ticket-credential"));

        assertEquals(
                NotificationErrorCode.MESSAGE_RATE_EXCEEDED,
                result.results().get("ticket-rate").errorCode()
        );
        assertEquals(true, result.results().get("ticket-rate").errorCode().isRetryable());
        assertEquals(
                NotificationErrorCode.INVALID_CREDENTIALS,
                result.results().get("ticket-credential").errorCode()
        );
        assertEquals(false, result.results().get("ticket-credential").errorCode().isRetryable());
    }

    private static PushMessage message() {
        return new PushMessage(
                "ExpoPushToken[placeholder-value]",
                "default",
                "grading",
                "채점이 완료됐어요",
                "모의고사 결과와 피드백을 확인해 보세요.",
                Map.of(
                        "type", "EXAM_GRADING_COMPLETED",
                        "notificationId", "notification-id",
                        "examId", "exam-id",
                        "deepLink", "/exams/exam-id/summary"
                )
        );
    }

    private static NotificationPushProperties properties(String accessToken) {
        return new NotificationPushProperties(
                true,
                "expo",
                new NotificationPushProperties.Expo(
                        SEND_URL,
                        RECEIPT_URL,
                        accessToken,
                        false
                ),
                Duration.ofSeconds(5),
                Duration.ofMinutes(15),
                Duration.ofMinutes(2),
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(30),
                100,
                Duration.ofSeconds(3),
                Duration.ofSeconds(10)
        );
    }
}
