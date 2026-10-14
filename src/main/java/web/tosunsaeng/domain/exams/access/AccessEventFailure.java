package web.tosunsaeng.domain.exams.access;

/** Fixed codes only; never retain a parser exception, token or request body. */
public final class AccessEventFailure extends RuntimeException {
    private final int status;
    private final String code;
    public AccessEventFailure(int status,String code) { super(code); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
}
