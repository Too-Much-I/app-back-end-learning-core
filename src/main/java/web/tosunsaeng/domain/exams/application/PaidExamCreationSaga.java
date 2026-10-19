package web.tosunsaeng.domain.exams.application;

import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import web.tosunsaeng.domain.exams.billing.*;
import web.tosunsaeng.domain.exams.billing.reconciliation.ReservationAuthCircuit;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.ExamCreationState;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import java.time.Clock;

/** Public adapter for paid schema2; no new DTO, right selection, or worker reserve path. */
@Service
public class PaidExamCreationSaga {
    private final ExamCreationOperationRepository operations;
    private final PaidExamPreparationService preparation;
    private final PaidReservationEvidenceStore evidence;
    private final PaidExamRecoveryService recovery;
    private final PaidExamLifecycleService lifecycle;
    private final BillingExamCreationSaga legacy;
    private final BillingReservationClient billing;
    private final BillingSagaProperties properties;
    private final ReservationAuthCircuit circuit;
    private final MongoTemplate mongo;
    private final MockExamCatalogService catalog;
    private final Clock clock;
    public PaidExamCreationSaga(ExamCreationOperationRepository operations, PaidExamPreparationService preparation,
            PaidReservationEvidenceStore evidence, PaidExamRecoveryService recovery, PaidExamLifecycleService lifecycle,
            BillingExamCreationSaga legacy, BillingReservationClient billing, BillingSagaProperties properties,
            ReservationAuthCircuit circuit, MongoTemplate mongo, MockExamCatalogService catalog, Clock clock) {
        this.operations=operations; this.preparation=preparation; this.evidence=evidence; this.recovery=recovery;
        this.lifecycle=lifecycle; this.legacy=legacy; this.billing=billing; this.properties=properties;
        this.circuit=circuit; this.mongo=mongo; this.catalog=catalog; this.clock=clock;
    }
    public ExamSessionManager.Assignment start(String user, String rawKey) {
        String key=ExamCreationIdempotencyKey.parse(rawKey);
        if (!properties.isCreationSagaEnabled()) throw new IllegalStateException("Paid saga requires Billing saga");
        try {
            var op=operations.findByUserIdAndOperationId(user,key).orElse(null);
            if (op!=null && op.getPaidSelection()==null) return legacy.start(user,key); // Drain existing schema only.
            if (op==null) {
                preparation.requireDiscoveryAllowed(user);
                BillingReservationClient.PhoneContinuationSnapshot phone=null;
                if (properties.isPhoneContinuationEnabled()
                        && !mongo.exists(Query.query(Criteria.where("userId").is(user)),ExamSession.class)
                        && !mongo.exists(Query.query(Criteria.where("userId").is(user)),"learning_record_billing_continuations")) {
                    if (circuit.blocked()) throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE);
                    phone=billing.findPhoneContinuation(user).orElse(null);
                }
                op=preparation.prepare(user,key,phone);
            }
            if (op.getPaidRequestDigest()==null && !op.isTerminal()) {
                if (op.isPhoneContinuation()) evidence.preparePhoneRequest(user,op.getCommandId());
                else evidence.prepareRequest(user,op.getCommandId());
            }
            // One reserve+confirm pass and one status pass. No client-generated replacement key.
            for(int pass=0;pass<2 && !op.isTerminal();pass++) op=recovery.resumeHttp(user,op.getCommandId(),clock.getZone());
            if (!op.isTerminal()) throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING,1);
            op=lifecycle.replay(user,op.getCommandId());
            if (op.getState()!=ExamCreationState.SUCCEEDED) {
                if ("ENTITLEMENT_REVOKED".equals(op.getFailureCategory())) throw new ExamsException(ErrorStatus._ENTITLEMENT_REVOKED);
                throw new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE,1);
            }
            var session=mongo.findById(op.getSessionId(),ExamSession.class);
            if (session==null) throw new ExamsException(ErrorStatus._EXAM_NOT_FOUND);
            return new ExamSessionManager.Assignment(session,catalog.getRequiredExam(op.getMockExamId()),false);
        } catch (BillingClientException failure) {
            if(failure.category()==BillingClientException.Category.AUTH_FAILURE) {
                try { circuit.blockFromHttp(); } catch(RuntimeException ignored) { }
            }
            throw switch(failure.category()) {
                case ENTITLEMENT_REVOKED -> new ExamsException(ErrorStatus._ENTITLEMENT_REVOKED);
                case TRANSITION_CONTEXT_CONFLICT -> new ExamsException(ErrorStatus._EXAM_TRANSITION_CONFLICT);
                case ENTITLEMENT_INSUFFICIENT -> new ExamsException(ErrorStatus._ENTITLEMENT_INSUFFICIENT);
                case IDEMPOTENCY_CONFLICT -> new ExamsException(ErrorStatus._IDEMPOTENCY_KEY_CONFLICT);
                case PROCESSING -> new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING,1);
                case RATE_LIMITED -> new ExamsException(ErrorStatus._BILLING_RATE_LIMITED,failure.retryAfterSeconds()==null?1:failure.retryAfterSeconds());
                case TEMPORARILY_UNAVAILABLE, MEMBER_EVIDENCE_PENDING, OPERATION_NOT_FOUND -> new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE,1);
                default -> new ExamsException(ErrorStatus._BILLING_TEMPORARILY_UNAVAILABLE);
            };
        } catch (org.springframework.dao.DataAccessException | IllegalStateException unavailable) {
            throw new ExamsException(ErrorStatus._EXAM_CREATION_PROCESSING,1);
        }
    }
}
