package web.tosunsaeng.domain.notification;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.function.Supplier;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {
    MockMvc mvc;
    NotificationStore store;
    NotificationProperties properties;
    static final String OWNER = "30000000-0000-4000-8000-000000000001";
    static final String DEVICE = "30000000-0000-4000-8000-000000000002";
    static final String PROOF = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    @BeforeEach void setup() {
        store = mock(NotificationStore.class); var tx = mock(NotificationTransactions.class);
        when(tx.run(any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("store", store); beans.registerSingleton("tx", tx);
        properties = new NotificationProperties(); properties.setApiEnabled(true);
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(beans.getBeanProvider(NotificationStore.class),
                beans.getBeanProvider(NotificationTransactions.class), properties, Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC)))
                .setControllerAdvice(new NotificationExceptionAdvice()).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void authenticate(String type) {
        var jwt = Jwt.withTokenValue("fixture-jwt").header("alg", "RS256").subject(OWNER).claim("account_type", type).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
    }
    String body(String extra) { return "{\"installationId\":\"" + DEVICE + "\",\"platform\":\"IOS\",\"pushToken\":\"fixture-token-1234567890\"" + extra + "}"; }
    @Test void jwtOwnerOnlyAndExistingTmi63ShapeDefaultsUnknown() throws Exception {
        authenticate("MEMBER");
        mvc.perform(put("/api/v1/notifications/devices").header("X-Installation-Secret", PROOF).contentType("application/json").content(body("")))
                .andDo(result -> { if (result.getResolvedException() != null) throw new AssertionError(result.getResolvedException()); })
                .andExpect(status().isOk()).andExpect(jsonPath("isSuccess").value(true)).andExpect(jsonPath("result.pushToken").doesNotExist());
        verify(store).register(eq(OWNER), eq("MEMBER"), eq(DEVICE), eq(PROOF), eq("IOS"), eq("fixture-token-1234567890"), eq("UNKNOWN"), any());
        mvc.perform(put("/api/v1/notifications/devices").header("X-Installation-Secret", PROOF).contentType("application/json").content(body(",\"userId\":\"untrusted\"")))
                .andExpect(status().isBadRequest());
    }
    @Test void guestCanRegisterSharedDeviceButMissingOrLegacyAuthenticationDenied() throws Exception {
        mvc.perform(delete("/api/v1/notifications/devices/" + DEVICE).header("X-Installation-Secret", PROOF)).andExpect(status().isForbidden());
        authenticate("GUEST");
        mvc.perform(put("/api/v1/notifications/devices").header("X-Installation-Secret", PROOF).contentType("application/json").content(body(""))).andExpect(status().isOk());
        verify(store).register(eq(OWNER), eq("GUEST"), anyString(), anyString(), anyString(), anyString(), eq("UNKNOWN"), any());
    }
    @Test void permissionNeedsTimestampAndDisabledFeatureRejects() throws Exception {
        authenticate("MEMBER");
        mvc.perform(put("/api/v1/notifications/devices").header("X-Installation-Secret", PROOF).contentType("application/json").content(body(",\"permission\":\"AUTHORIZED\""))).andExpect(status().isBadRequest());
        properties.setApiEnabled(false);
        mvc.perform(delete("/api/v1/notifications/devices/" + DEVICE).header("X-Installation-Secret", PROOF)).andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
}
