package web.tosunsaeng.domain.promotion;

final class PromotionFailure extends RuntimeException {
    final int status;
    final String code;
    PromotionFailure(int status, String code) { super(code); this.status=status; this.code=code; }
    static PromotionFailure invalid() { return new PromotionFailure(400,"PROMOTION_INVALID_REQUEST"); }
    static PromotionFailure unavailable() { return new PromotionFailure(503,"PROMOTION_UNAVAILABLE"); }
}
