package web.tosunsaeng.domain.notification;

import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.*;
import io.micrometer.core.instrument.MeterRegistry;
import web.tosunsaeng.domain.promotion.PromotionProperties;

public class PromotionPushWorker implements AutoCloseable {
    private final NotificationStore store;
    private final NotificationTransactions tx;
    private final PromotionPushProperties config;
    private final PromotionProperties campaign;
    private final PromotionEligibilityClient eligibility;
    private final PushGateway gateway;
    private final Clock clock;
    private final MeterRegistry meters;
    private String cursor;
    public PromotionPushWorker(NotificationStore store,NotificationTransactions tx,PromotionPushProperties config,
            PromotionProperties campaign,PromotionEligibilityClient eligibility,PushGateway gateway,Clock clock,MeterRegistry meters){
        this.store=store;this.tx=tx;this.config=config;this.campaign=campaign;this.eligibility=eligibility;
        this.gateway=gateway;this.clock=clock;this.meters=meters;
    }
    public synchronized void tick(){
        try{
            if(!window() || store.paused(clock.instant())){cursor=null;return;}
            var devices=store.candidates(cursor,config.getBatchSize());
            if(devices.isEmpty()){cursor=null;return;}
            for(Document d:devices){
                if(Thread.currentThread().isInterrupted() || !window() || store.paused(clock.instant()))break;
                cursor=d.getString("_id");
                try{process(cursor);}catch(RuntimeException failure){count("LOCAL_FAILURE");}
            }
        }catch(RuntimeException failure){count("SWEEP_FAILURE");}
    }
    boolean window(){return config.isEnabled() && campaign.isEnabled()
            && PromotionPushPolicy.inWindow(clock.instant(),campaign.getOpensAt(),campaign.getClosesAt());}
    boolean eligibleDevice(Document d){
        Instant now=clock.instant();
        if(d==null || !Boolean.TRUE.equals(d.getBoolean("active")) || !"MEMBER".equals(d.getString("accountType"))
                || !"AUTHORIZED".equals(d.getString("permission")) || d.getString("pushToken")==null
                || d.getString("userId")==null || d.getDate("permissionObservedAt")==null
                || d.getDate("permissionObservedAt").toInstant().isBefore(now.minus(Duration.ofDays(30)))
                || d.getDate("lastSeenAt")==null || d.getDate("lastSeenAt").toInstant().isBefore(now.minus(Duration.ofDays(90))))return false;
        String owner=d.getString("userId");
        if(store.blocked(owner) || store.mongo.exists(Query.query(Criteria.where("userId").is(owner)
                .and("activeGuard").is(true)),"learning_record_deletion_operations"))return false;
        return store.mongo.count(Query.query(Criteria.where("promotionId").is(campaign.getCampaignId())
                .and("userId").is(owner)).limit(4),"promotion_applications")<4;
    }
    void process(String installation){
        Document d=store.device(installation);
        if(!window() || !eligibleDevice(d))return;
        String owner=d.getString("userId"),marker=PromotionPushPolicy.marker(campaign.getCampaignId(),owner,clock.instant());
        if(store.mongo.exists(Query.query(Criteria.where("_id").is(marker)),NotificationStore.CONTROL))return;
        if(!eligibility.eligible(owner))return;
        if(config.isDryRun()){count("DRY_RUN_ELIGIBLE");return;}
        try{gateway.prepare();}catch(PushGateway.PreparationFailure failure){store.pause(failure.result,clock.instant());count("PREPARATION_FAILED");return;}
        NotificationStore.Claim claim;
        try{claim=tx.run(()->{
            Document current=store.device(installation);
            if(!window() || !eligibleDevice(current) || !owner.equals(current.getString("userId"))
                    || !Objects.equals(d.get("version"),current.get("version")) || store.paused(clock.instant()))return null;
            store.touchOwner(owner);
            store.mongo.updateFirst(Query.query(Criteria.where("_id").is(installation)),new Update().inc("version",1L),NotificationStore.DEVICES);
            store.mongo.insert(new Document("_id",marker).append("type",PromotionPushPolicy.TYPE)
                    .append("status","SENDING").append("attemptedAt",Date.from(clock.instant()))
                    .append("expiresAt",Date.from(clock.instant().plus(Duration.ofDays(31)))),NotificationStore.CONTROL);
            return new NotificationStore.Claim(marker,installation,owner,current.getString("pushToken"),current.getString("tokenHash"),
                    ((Number)current.get("version")).longValue()+1);
        });}catch(DuplicateKeyException duplicate){return;}
        if(claim==null)return; // Unknown commit throws before any FCM call.
        PushGateway.Result result;
        try{
            boolean remotelyEligible=eligibility.eligible(owner);
            Document latest=store.device(installation);
            boolean allowed=remotelyEligible && window() && !store.paused(clock.instant()) && eligibleDevice(latest)
                    && owner.equals(latest.getString("userId")) && Objects.equals(latest.get("version"),claim.version);
            result=allowed?gateway.send(claim.token,NotificationStore.hash(marker),PromotionPushPolicy.expires(clock.instant(),campaign.getClosesAt()))
                    :PushGateway.Result.skipped();
        }catch(RuntimeException failure){result=PushGateway.Result.unknown();}
        store.pause(result,clock.instant());
        store.mongo.updateFirst(Query.query(Criteria.where("_id").is(marker).and("status").is("SENDING")),new Update()
                .set("status",result.status().name()).set("finishedAt",Date.from(clock.instant())),NotificationStore.CONTROL);
        // Existing conditional token invalidation does not affect daily reminder deduplication.
        if(result.invalidToken())store.finish(claim,result,clock.instant());
        count(result.status().name());
    }
    private void count(String result){meters.counter("notification.promotion","outcome",result).increment();}
    @Override public synchronized void close() throws Exception {
        try{eligibility.close();}finally{if(gateway instanceof AutoCloseable closeable)closeable.close();}
    }
}
