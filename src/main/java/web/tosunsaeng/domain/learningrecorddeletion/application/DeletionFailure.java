package web.tosunsaeng.domain.learningrecorddeletion.application;

public final class DeletionFailure extends RuntimeException {
    private final int status;
    private final String code;
    public DeletionFailure(int status, String code) { super(code); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
    public static DeletionFailure blocked() { return new DeletionFailure(409, "LEARNING_RECORD_DELETION_IN_PROGRESS"); }
    public static DeletionFailure temporary() { return new DeletionFailure(503, "LEARNING_RECORD_DELETION_TEMPORARILY_UNAVAILABLE"); }
}
