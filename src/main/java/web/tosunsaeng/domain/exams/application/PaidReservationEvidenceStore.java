package web.tosunsaeng.domain.exams.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.billing.BillingReservationClient.ReservationSnapshot;
import web.tosunsaeng.domain.exams.billing.PaidReservationProtocol;
import web.tosunsaeng.domain.exams.billing.BillingSagaConfiguration;
import web.tosunsaeng.domain.exams.domain.entity.ExamCreationOperation;
import web.tosunsaeng.domain.exams.domain.entity.ExamSession;
import web.tosunsaeng.domain.exams.domain.repository.ExamCreationOperationRepository;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;

/** P2 persistence boundary only: no network, Session creation, terminal transition or guard release. */
@Service
public class PaidReservationEvidenceStore {
    private final ExamCreationOperationRepository operations;
    private final MongoTemplate mongo;
    private final ObjectProvider<UserOwnedTransactionExecutor> owners;
    private final Clock clock;
    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new BillingSagaConfiguration().billingContractObjectMapper();

    public PaidReservationEvidenceStore(ExamCreationOperationRepository operations, MongoTemplate mongo,
            ObjectProvider<UserOwnedTransactionExecutor> owners, Clock clock) {
        this.operations = operations; this.mongo = mongo; this.owners = owners; this.clock = clock;
    }

    /** Persist before sending. The same normalized request always produces the same digest. */
    public PaidReservationProtocol.Request prepareRequest(String userId, String commandId) {
        return owner().execute(userId, () -> {
            ExamCreationOperation op = load(userId, commandId);
            var request = PaidReservationProtocol.request(op);
            op.fixPaidRequestDigest(digest(request));
            operations.save(op);
            return request;
        });
    }

    public PaidReservationProtocol.PhoneRequest preparePhoneRequest(String userId, String commandId) {
        return owner().execute(userId, () -> {
            ExamCreationOperation op = load(userId, commandId);
            var request = PaidReservationProtocol.phoneRequest(op);
            op.fixPaidRequestDigest(digest(request));
            operations.save(op);
            return request;
        });
    }

    /** Only full reserve/status snapshots enter here; confirm/cancel's abbreviated DTO must not erase evidence. */
    public void observe(String userId, String commandId, ReservationSnapshot snapshot) {
        owner().execute(userId, () -> {
            ExamCreationOperation op = load(userId, commandId);
            Object request = op.isPhoneContinuation() ? PaidReservationProtocol.phoneRequest(op) : PaidReservationProtocol.request(op);
            PaidReservationProtocol.require(Objects.equals(op.getPaidRequestDigest(), digest(request)));
            op.observePaidReservation(snapshot);
            ExamSession session = mongo.findById(op.getSessionId(), ExamSession.class);
            if (session != null) {
                PaidReservationProtocol.require(Objects.equals(userId, session.getUserId()));
                session.observePaidReservation(op.getPaidReservation());
                mongo.save(session);
            }
            operations.save(op);
            return null;
        });
    }

    private ExamCreationOperation load(String userId, String commandId) {
        PaidReservationProtocol.require(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        owner().requirePublicWrite(userId);
        PaidReservationProtocol.require(!mongo.exists(Query.query(Criteria.where("_id").is(userId)
                .and("blockedUntil").gt(Date.from(clock.instant()))), "withdrawn_user_access_denies"));
        ExamCreationOperation op = operations.findById(commandId).orElseThrow();
        PaidReservationProtocol.require(Objects.equals(userId, op.getUserId()) && op.getPaidSelection() != null);
        return op;
    }

    private UserOwnedTransactionExecutor owner() {
        var owner = owners.getIfAvailable();
        PaidReservationProtocol.require(owner != null && owner.enabled());
        return owner;
    }

    static String digest(Object request) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(request)));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Paid request digest unavailable");
        }
    }
}
