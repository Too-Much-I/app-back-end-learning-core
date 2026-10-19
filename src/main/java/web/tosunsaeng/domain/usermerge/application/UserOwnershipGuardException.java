package web.tosunsaeng.domain.usermerge.application;

import lombok.Getter;

@Getter
public final class UserOwnershipGuardException extends RuntimeException {

    private final Reason reason;

    public UserOwnershipGuardException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public enum Reason {
        ALREADY_MERGED,
        STATE_CONFLICT
    }
}
