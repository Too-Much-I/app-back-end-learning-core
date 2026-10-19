package web.tosunsaeng.domain.notification;

/** Fixed messages only: never attach a token, credential, request or provider exception. */
public class NotificationFailure extends RuntimeException {
    public final int status;
    public NotificationFailure(int status) { super("NOTIFICATION_" + status); this.status = status; }
}
