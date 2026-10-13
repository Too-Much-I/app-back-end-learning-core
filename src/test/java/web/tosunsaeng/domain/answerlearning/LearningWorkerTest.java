package web.tosunsaeng.domain.answerlearning;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Update;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;

class LearningWorkerTest {
    LearningStore store=mock(LearningStore.class);
    LearningLambdaClient lambda=mock(LearningLambdaClient.class);
    LearningWorker worker=new LearningWorker(store,lambda,config(),CLOCK,new SimpleMeterRegistry());
    @Test void queuedJobUsesStableIdAndNextPollOnlyReads() {
        var s=submission(); s.status="queued"; when(lambda.submit(s)).thenReturn(remote(s,"transcribing"));
        worker.process(s); verify(lambda).submit(s);
        var updates=org.mockito.ArgumentCaptor.forClass(Update.class); verify(store).finish(eq(s),updates.capture(),eq(NOW),eq(2L));
        assertThat(updates.getValue().getUpdateObject().get("$set",Document.class).getBoolean("dispatched")).isTrue();
        s.dispatched=true; when(lambda.get(s)).thenReturn(completed(s)); worker.process(s); verify(lambda).get(s);
    }
    @Test void ambiguousTransportKeepsAttemptAndBacksOffWithoutFakeScore() {
        var s=submission(); when(lambda.submit(s)).thenThrow(LearningFailure.unavailable()); worker.process(s);
        var c=org.mockito.ArgumentCaptor.forClass(Update.class); verify(store).finish(eq(s),c.capture(),eq(NOW),eq(2L));
        assertThat(c.getValue().getUpdateObject().get("$inc",Document.class)).containsEntry("transportFailures",1);
        assertThat(c.getValue().getUpdateObject().toJson()).doesNotContain("attempt", "result", "completed");
        assertThat(s.attempt).isEqualTo(1);
    }
    @Test void modelFailedAndNoSpeechAreNotCompleted() {
        var s=submission();
        var fail=remote(s,"failed").append("error",new Document("code","DECISIONS_UNAVAILABLE").append("message","private provider body").append("retryable",true));
        Document sets=LearningWorker.validate(s,fail).getUpdateObject().get("$set",Document.class);
        assertThat(sets).containsEntry("status","failed").containsEntry("result",null);
        assertThat(((LearningModels.Error)sets.get("error")).message()).doesNotContain("private provider");
        s.attempt=3; assertThat(((LearningModels.Error)LearningWorker.validate(s,remote(s,"failed").append("error",fail.get("error")))
                .getUpdateObject().get("$set",Document.class).get("error")).retryable()).isFalse();
        assertThat(LearningWorker.validate(s,remote(s,"no_speech")).getUpdateObject().get("$set",Document.class)).containsEntry("result",null);
    }
    @Test void resultIsWhitelistedAndCompletionRepairedWithoutAnotherModelCall() {
        var s=submission();
        Document result=LearningWorker.validate(s,completed(s)).getUpdateObject().get("$set",Document.class).get("result",Document.class);
        assertThat(result).containsOnlyKeys("checklistVersion","transcript","metCount","totalCount","items");
        s.status="completed"; s.completionPending=true; s.result=result; worker.process(s);
        verify(store).completeClaim(s,NOW); verifyNoInteractions(lambda);
    }
    @Test void rejectsWrongIdentityCountsDuplicateItemsAndInvalidProbability() {
        var s=submission(); var response=completed(s); response.put("level","AL");
        assertThatThrownBy(()->LearningWorker.validate(s,response)).hasMessage("INVALID_GRADING_RESPONSE");
        var wrongCount=completed(s); wrongCount.get("result",Document.class).put("metCount",0);
        assertThatThrownBy(()->LearningWorker.validate(s,wrongCount)).hasMessage("INVALID_GRADING_RESPONSE");
        var badProbability=completed(s); badProbability.get("result",Document.class).getList("items",Document.class).getFirst().put("probability",Double.NaN);
        assertThatThrownBy(()->LearningWorker.validate(s,badProbability)).hasMessage("INVALID_GRADING_RESPONSE");
        var wrongVersion=completed(s); wrongVersion.get("result",Document.class).put("checklistVersion","other");
        assertThatThrownBy(()->LearningWorker.validate(s,wrongVersion)).hasMessage("INVALID_GRADING_RESPONSE");
    }
    @Test void uncertainProbabilityStaysUncertain() {
        var s=submission(); var response=completed(s); var result=response.get("result",Document.class);
        result.put("metCount",0); result.getList("items",Document.class).getFirst().put("probability",.5);
        result.getList("items",Document.class).getFirst().put("verdict","uncertain");
        assertThat(LearningWorker.validate(s,response).getUpdateObject().get("$set",Document.class).get("result",Document.class).getInteger("metCount")).isZero();
    }
    @Test void expiredJobsCannotBeDispatchedAfterLambdaReceiptExpiry() {
        var s=submission(); s.queuedAt=NOW.minusSeconds(86401); worker.process(s);
        verifyNoInteractions(lambda);
        var c=org.mockito.ArgumentCaptor.forClass(Update.class); verify(store).finish(eq(s),c.capture(),eq(NOW),eq(0L));
        assertThat(c.getValue().getUpdateObject().get("$set",Document.class)).containsEntry("status","failed");
    }
    @Test void backgroundDrainsDurableJobsWithoutAppPollingAndSkipsLostClaims() {
        var s=submission(); when(store.due(NOW,null)).thenReturn(List.of(s)); when(store.claim(s,NOW)).thenReturn(null);
        worker.tick(); verifyNoInteractions(lambda);
        when(store.claim(s,NOW)).thenReturn(s); when(lambda.submit(s)).thenReturn(remote(s,"queued")); worker.tick(); verify(lambda).submit(s);
    }
}
