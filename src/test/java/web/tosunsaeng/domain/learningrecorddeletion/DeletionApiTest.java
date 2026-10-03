package web.tosunsaeng.domain.learningrecorddeletion;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import web.tosunsaeng.domain.learningrecorddeletion.api.*;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionOperation;
import web.tosunsaeng.global.auth.CurrentUserProvider;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeletionApiTest {
    final DeletionCommandService service = mock(DeletionCommandService.class);
    final CurrentUserProvider users = mock(CurrentUserProvider.class);
    final DeletionController controller = new DeletionController(service, users);
    MockMvc mvc;
    static final String OWNER = "00000000-0000-0000-0000-000000000001";
    static final String KEY = "139f345b-9be8-43a3-a63d-9108df972741";
    @BeforeEach void setup() {
        when(users.getCurrentUserId()).thenReturn(OWNER);
        ReflectionTestUtils.setField(controller, "commandEnabled", true);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new DeletionExceptionAdvice()).build();
    }
    @Test void acceptedHasOnlyAllowlistedProjectionAndExistingEnvelope() throws Exception {
        when(service.request(OWNER, KEY)).thenReturn(DeletionOperation.requested(KEY, OWNER, Instant.parse("2026-10-03T00:00:00Z")));
        mvc.perform(delete("/api/v1/learning-records").header("Idempotency-Key", KEY))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.status").value("processing")).andExpect(jsonPath("$.result.canStartLearning").value(false))
                .andExpect(jsonPath("$.result.requestedAt").value("2026-10-03T00:00:00Z"))
                .andExpect(jsonPath("$.result.userId").doesNotExist()).andExpect(jsonPath("$.result.stage").doesNotExist())
                .andExpect(jsonPath("$.result.attemptGroupId").doesNotExist());
    }
    @Test void rejectsBodyOrQueryWithoutInvokingCommand() throws Exception {
        mvc.perform(delete("/api/v1/learning-records").content("{}")) .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/learning-records?userId=not-allowed")) .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void disablingNewCommandsKeepsStatusAvailable() throws Exception {
        ReflectionTestUtils.setField(controller, "commandEnabled", false);
        mvc.perform(delete("/api/v1/learning-records").header("Idempotency-Key", KEY)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/learning-records/deletion")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("not_requested")).andExpect(jsonPath("$.result.canStartLearning").value(true));
        verify(service, never()).request(anyString(), anyString());
    }
}
