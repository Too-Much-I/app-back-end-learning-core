package web.tosunsaeng.domain.notifications.api;

import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import web.tosunsaeng.domain.notifications.dto.NotificationDeviceRequest;
import web.tosunsaeng.domain.notifications.dto.NotificationDeviceResponse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDeviceApiContractTest {

    @Test
    void controllerExposesOnlyTheTwoDeviceEndpoints() throws Exception {
        RequestMapping root = NotificationDeviceController.class.getAnnotation(RequestMapping.class);
        Method register = NotificationDeviceController.class.getMethod(
                "register", NotificationDeviceRequest.Register.class);
        Method disable = NotificationDeviceController.class.getMethod("disable", String.class);

        assertEquals("/api/v1/notifications/devices", root.value()[0]);
        assertEquals(0, register.getAnnotation(PutMapping.class).value().length);
        assertEquals("/{installationId}", disable.getAnnotation(DeleteMapping.class).value()[0]);
        assertTrue(register.getAnnotation(Operation.class).description().contains("ExpoPushToken"));
        assertTrue(disable.getAnnotation(Operation.class).description().contains("Bearer"));
    }

    @Test
    void requestAndResponsesContainNoInternalIdentityOrTokenFields() {
        Set<String> requestFields = Arrays.stream(
                        NotificationDeviceRequest.Register.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("installationId", "platform", "expoPushToken"), requestFields);
        assertFalse(requestFields.contains("userId"));

        assertEquals(
                Set.of("registered"),
                Arrays.stream(NotificationDeviceResponse.Registered.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .collect(Collectors.toSet())
        );
        assertEquals(
                Set.of("disabled"),
                Arrays.stream(NotificationDeviceResponse.Disabled.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .collect(Collectors.toSet())
        );
    }
}
