package web.tosunsaeng.domain.exams.billing;

public class BillingClientException extends RuntimeException {

    private final Category category;
    private final Integer retryAfterSeconds;

    public BillingClientException(Category category, Integer retryAfterSeconds, Throwable cause) {
        super(category.name(), cause);
        this.category = category;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public BillingClientException(Category category, Integer retryAfterSeconds) {
        this(category, retryAfterSeconds, null);
    }

    public Category category() {
        return category;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public enum Category {
        INVALID_REQUEST,
        ENTITLEMENT_INSUFFICIENT,
        PROCESSING,
        IDEMPOTENCY_CONFLICT,
        RESERVATION_CONFLICT,
        OPERATION_NOT_FOUND,
        RATE_LIMITED,
        TEMPORARILY_UNAVAILABLE,
        CONTRACT_ERROR
    }
}
