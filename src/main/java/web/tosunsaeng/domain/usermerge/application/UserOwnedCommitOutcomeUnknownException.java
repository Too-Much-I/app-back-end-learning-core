package web.tosunsaeng.domain.usermerge.application;

public class UserOwnedCommitOutcomeUnknownException extends RuntimeException {

    public UserOwnedCommitOutcomeUnknownException(Throwable cause) {
        super("User-owned Mongo transaction commit outcome is unknown", cause);
    }
}
