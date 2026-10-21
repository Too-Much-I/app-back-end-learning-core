package web.tosunsaeng.domain.exams.reanswer;

import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

final class ReanswerFailures {
    private ReanswerFailures() {}
    static ExamsException unavailable() {
        return new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE, 1);
    }
    static IllegalStateException contract() {
        return contract("protocol",0);
    }
    static ContractFailure contract(String category,int status) {
        // Generic handler reports a 500 to Sentry. Never retain remote bodies/tokens/PII as causes.
        return new ContractFailure("Reanswer Billing failure category="+category+" status="+status);
    }
    static final class ContractFailure extends IllegalStateException {
        private ContractFailure(String message) { super(message); }
    }
}
