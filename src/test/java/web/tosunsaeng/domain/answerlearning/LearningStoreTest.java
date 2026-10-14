package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import java.util.function.Supplier;
import web.tosunsaeng.domain.usermerge.application.UserOwnedTransactionExecutor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

class LearningStoreTest {
    MongoTemplate mongo=mock(MongoTemplate.class);
    LearningStore store=new LearningStore(mongo,null);
    @Test void foreignOwnerCannotReadGloballyUniqueId() {
        when(mongo.findById(ID,Submission.class)).thenReturn(submission());
        assertThatThrownBy(()->store.owned("other",ID)).hasMessage("LEARNING_NOT_FOUND");
        assertThat(store.owned(OWNER,ID).userId).isEqualTo(OWNER);
    }
    @Test void staleWorkersCanOnlyWriteWithCurrentAttemptAndLeaseToken() {
        var s=submission(); s.attempt=2;
        store.finish(s,new Update().set("status","completed"),NOW,0);
        var query=org.mockito.ArgumentCaptor.forClass(Query.class); verify(mongo).updateFirst(query.capture(),any(Update.class),eq(Submission.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id",ID).containsEntry("attempt",2).containsEntry("leaseToken","claim");
    }
    @Test void concurrentRetryUsesFailureAndAttemptConditions() {
        var s=submission(); store.retry(s,NOW,60);
        var query=org.mockito.ArgumentCaptor.forClass(Query.class); var update=org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(),update.capture(),any(FindAndModifyOptions.class),eq(Submission.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("status","failed").containsEntry("attempt",1).containsEntry("error.retryable",true);
        assertThat(update.getValue().getUpdateObject().get("$inc",Document.class)).containsEntry("attempt",1);
    }
    @Test void quotaExhaustionIsAnAtomicConditionalUpsertConflict() {
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Document.class),eq("learning_submission_limits")))
                .thenThrow(new DuplicateKeyException("duplicate synthetic key"));
        assertThatThrownBy(()->store.create(submission(),2)).hasMessage("LEARNING_RATE_LIMITED");
    }
    @Test void completionNeverStoresIntermediateProgressAndRepeatsUseBusinessKey() {
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Submission.class))).thenReturn(submission());
        store.complete(OWNER,"lesson","IH","test-v1",ID,NOW);
        store.complete(OWNER,"lesson","IH","test-v1",ID,NOW.plusSeconds(10));
        var query=org.mockito.ArgumentCaptor.forClass(Query.class); var update=org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo,times(2)).upsert(query.capture(),update.capture(),eq("learning_completions"));
        assertThat(query.getAllValues().get(0).getQueryObject()).isEqualTo(query.getAllValues().get(1).getQueryObject());
        assertThat(update.getValue().getUpdateObject()).containsOnlyKeys("$setOnInsert");
    }
    @Test void writesParticipateInExistingOwnerFenceWhenEnabled() {
        var guard=mock(UserOwnedTransactionExecutor.class);
        when(guard.execute(eq(OWNER),any())).thenAnswer(i->((Supplier<?>)i.getArgument(1)).get());
        new LearningStore(mongo,guard).create(submission(),60); verify(guard).execute(eq(OWNER),any());
    }
}
