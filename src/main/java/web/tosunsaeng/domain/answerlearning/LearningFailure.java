package web.tosunsaeng.domain.answerlearning;

/** Only fixed codes/messages: never wrap provider bodies, recordings or transcripts. */
public final class LearningFailure extends RuntimeException {
    public final int status;
    public final String code;
    public LearningFailure(int status, String code) { super(code); this.status = status; this.code = code; }
    public static LearningFailure bad() { return new LearningFailure(400, "INVALID_REQUEST"); }
    public static LearningFailure missing() { return new LearningFailure(404, "LEARNING_NOT_FOUND"); }
    public static LearningFailure unavailable() { return new LearningFailure(503, "LEARNING_UNAVAILABLE"); }
}
