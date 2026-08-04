package web.tosunsaeng.global.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "management.health.mongo.enabled=false",
        "management.health.redis.enabled=false"
})
class S3CredentialHealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AwsCredentialsProvider awsCredentialsProvider;

    @Test
    void healthIsUpWithoutResolvingAwsCredentials() throws Exception {
        String responseBody = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> response = objectMapper.readValue(
                responseBody,
                new TypeReference<>() {
                }
        );

        assertEquals("UP", response.get("status"));
        assertEquals(Set.of("status", "groups"), response.keySet());
        verify(awsCredentialsProvider, never()).resolveCredentials();
    }
}
