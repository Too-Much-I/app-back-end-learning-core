package web.tosunsaeng.domain.withdrawal.api;

public record UserWithdrawnEventRequest(
        String eventId,
        Integer schemaVersion,
        String userId,
        String withdrawnAt
) {
}
