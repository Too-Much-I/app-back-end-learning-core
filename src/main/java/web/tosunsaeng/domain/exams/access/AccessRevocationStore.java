package web.tosunsaeng.domain.exams.access;

import com.mongodb.*;
import org.bson.Document;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

/** One immutable document per group; event uniqueness is independent. No TTL, upsert or owner transfer. */
public final class AccessRevocationStore {
    public static final String COLLECTION="billing_access_revocations";
    public static final Document GROUP_INDEX=new Document("environment",1).append("producer",1).append("consumerScopeId",1).append("attemptGroupId",1);
    public static final Document EVENT_INDEX=new Document("environment",1).append("producer",1).append("consumerScopeId",1).append("eventId",1);
    private final MongoTemplate mongo;
    private final String environment,scope;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private volatile boolean ready;
    public AccessRevocationStore(MongoTemplate mongo,String environment,String scope,Clock clock) {
        if(environment==null || !Set.of("test","staging","production").contains(environment) || scope==null || scope.isBlank())
            throw new IllegalStateException("Billing access scope configuration is invalid");
        this.mongo=mongo; this.environment=environment; this.scope=scope; this.clock=clock;
        transaction=new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory(),TransactionOptions.builder()
                .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).readPreference(ReadPreference.primary()).build()));
        transaction.setTimeout(10);
    }
    public void validateIndexes() {
        ready=false;
        try {
            var indexes=mongo.getCollection(COLLECTION).listIndexes().into(new ArrayList<>());
            check(indexes,"uq_paid_revoke_group",GROUP_INDEX); check(indexes,"uq_paid_revoke_event",EVENT_INDEX);
            if(indexes.stream().anyMatch(i->i.containsKey("expireAfterSeconds"))) throw new IllegalStateException();
            ready=true;
        } catch(Exception invalid) { throw new IllegalStateException("Billing access indexes are not ready"); }
    }
    private static void check(List<Document> indexes,String name,Document key) {
        var found=indexes.stream().filter(i->name.equals(i.getString("name"))).findFirst().orElseThrow();
        if(!key.toJson().equals(found.get("key",Document.class).toJson()) || !Boolean.TRUE.equals(found.getBoolean("unique"))
                || found.containsKey("partialFilterExpression") || found.containsKey("sparse") || found.containsKey("collation")) throw new IllegalStateException();
    }
    private Document base() { return new Document("environment",environment).append("producer","billing").append("consumerScopeId",scope); }
    public void accept(AccessRevocationEvent event) {
        if(!ready) throw new AccessEventFailure(503,"LC_TEMPORARILY_UNAVAILABLE");
        if(!Objects.equals(scope,event.consumerScopeId())) throw new AccessEventFailure(422,"UNSUPPORTED_CONTRACT");
        for(int attempt=0;attempt<3;attempt++) {
            try {
                transaction.execute(status->{
                    if(classify(event,false)) return null;
                    // Existing owner only: serialize with writers without recreating a deleted/withdrawn account.
                    mongo.updateFirst(Query.query(Criteria.where("_id").is(event.userId())),new Update().inc("revision",1L),"user_ownership_guards");
                    Document fact=base().append("eventId",event.eventId()).append("attemptGroupId",event.attemptGroupId())
                            .append("digest",event.digest()).append("userId",event.userId()).append("reason",event.reason())
                            .append("occurredAt",event.occurredAt()).append("confirmedSessionId",event.confirmedSessionId())
                            .append("firstObservedAt",Date.from(clock.instant()));
                    mongo.insert(fact,COLLECTION);
                    // Never create Sessions. The immutable exception does not override owner/lifecycle checks.
                    Criteria denied=Criteria.where("attemptGroupId").is(event.attemptGroupId());
                    if(event.confirmedSessionId()!=null) denied=denied.orOperator(Criteria.where("_id").ne(event.confirmedSessionId()),
                            Criteria.where("userId").ne(event.userId()));
                    mongo.updateMulti(Query.query(denied),new Update().set("accessRevoked",true).inc("version",1L),"exam_sessions");
                    return null;
                });
                return;
            } catch(AccessEventFailure conflict) { throw conflict; }
            catch(RuntimeException failure) {
                // Fresh primary-majority read after duplicate/transient/unknown commit; no aborted-session reads.
                try { if(classify(event,true)) return; } catch(AccessEventFailure conflict) { throw conflict; }
                catch(RuntimeException unavailable) { throw new AccessEventFailure(503,"LC_TEMPORARILY_UNAVAILABLE"); }
                boolean retry=false;
                for(Throwable c=failure;c!=null;c=c.getCause()) {
                    if(c instanceof MongoException m && m.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))
                        throw new AccessEventFailure(503,"LC_TEMPORARILY_UNAVAILABLE");
                    if(c instanceof MongoException m && m.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) retry=true;
                    if(c instanceof org.springframework.dao.DuplicateKeyException) retry=true;
                }
                if(!retry) break;
            }
        }
        throw new AccessEventFailure(503,"LC_TEMPORARILY_UNAVAILABLE");
    }
    private boolean classify(AccessRevocationEvent e,boolean fresh) {
        var collection=fresh?mongo.getMongoDatabaseFactory().getMongoDatabase().getCollection(COLLECTION)
                .withReadConcern(ReadConcern.MAJORITY).withReadPreference(ReadPreference.primary()):mongo.getCollection(COLLECTION);
        Document byEvent=collection.find(base().append("eventId",e.eventId())).first();
        if(byEvent!=null) {
            if(!e.digest().equals(byEvent.getString("digest"))) throw new AccessEventFailure(409,"EVENT_ID_CONFLICT");
            return true;
        }
        if(collection.find(base().append("attemptGroupId",e.attemptGroupId())).first()!=null)
            throw new AccessEventFailure(409,"GROUP_REVOKE_CONFLICT");
        return false;
    }
    /** Must run within the caller's owner transaction before pending insert/final activation. */
    public boolean denied(String group,String session,String user) {
        if(group==null) return false;
        var fact=mongo.getCollection(COLLECTION).find(base().append("attemptGroupId",group)).first();
        return fact!=null && !(session!=null && session.equals(fact.getString("confirmedSessionId")) && user.equals(fact.getString("userId")));
    }
}
