package web.tosunsaeng.domain.challenge;

/** Fixed public/internal wire errors. Never retain input payloads or provider exceptions. */
public class ChallengeFailure extends RuntimeException {
    public final int status;
    public final String code;
    public final Object result;
    public ChallengeFailure(int status, String code) { this(status, code, null); }
    public ChallengeFailure(int status, String code, Object result) {
        super(code); this.status = status; this.code = code; this.result = result;
    }
    public static ChallengeFailure badRequest() { return new ChallengeFailure(400, "COMMON400"); }
    public static ChallengeFailure forbidden() { return new ChallengeFailure(403, "COMMON403"); }
    public static ChallengeFailure internal() { return new ChallengeFailure(500, "COMMON500"); }
}
