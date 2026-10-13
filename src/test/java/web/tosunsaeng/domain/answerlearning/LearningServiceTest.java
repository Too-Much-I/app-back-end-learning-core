package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningModels.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

class LearningServiceTest {
    LearningCatalog catalog=mock(LearningCatalog.class);
    LearningStore store=mock(LearningStore.class);
    LearningStorage storage=mock(LearningStorage.class);
    LearningProperties properties=config();
    LearningService service=new LearningService(catalog,store,storage,properties,CLOCK);
    @BeforeEach void setup() {
        when(catalog.lesson("lesson")).thenReturn(lesson(2));
        when(catalog.checklist(any())).thenReturn(submission().checklist);
        when(store.create(any(),anyInt())).thenAnswer(i->i.getArgument(0));
    }
    Create createRequest() { return new Create(ID,"lesson","IH","test-v1"); }
    @Test void snapshotsServerCriteriaAndStartsWithoutAudio() {
        Job job=service.create(OWNER,createRequest());
        assertThat(job.status()).isEqualTo("awaiting_audio"); assertThat(job.result()).isNull();
        var capture=org.mockito.ArgumentCaptor.forClass(Submission.class); verify(store).create(capture.capture(),eq(60));
        assertThat(capture.getValue().checklist).isEqualTo(submission().checklist);
        verifyNoInteractions(storage);
    }
    @Test void duplicateCreateReturnsExistingWithoutQuotaOrDispatch() {
        var s=submission(); when(store.find(ID)).thenReturn(s); when(store.owned(OWNER,ID)).thenReturn(s);
        assertThat(service.create(OWNER,createRequest()).id()).isEqualTo(ID);
        verify(store,never()).create(any(),anyInt()); verifyNoInteractions(catalog,storage);
    }
    @Test void duplicateWithDifferentLevelConflicts() {
        var s=submission(); when(store.find(ID)).thenReturn(s); when(store.owned(OWNER,ID)).thenReturn(s);
        assertThatThrownBy(()->service.create(OWNER,new Create(ID,"lesson","AL","test-v1")))
                .isInstanceOfSatisfying(LearningFailure.class,e->assertThat(e.status).isEqualTo(409));
    }
    @Test void foreignIdCannotBeCreatedOrSubmitted() {
        when(store.find(ID)).thenReturn(submission()); when(store.owned("other",ID)).thenThrow(LearningFailure.missing());
        assertThatThrownBy(()->service.create("other",createRequest())).isInstanceOf(LearningFailure.class);
        assertThatThrownBy(()->service.submit("other",ID)).isInstanceOf(LearningFailure.class); verifyNoInteractions(storage);
    }
    @Test void noCriteriaAndPartOneCannotCreatePaidJobs() {
        when(catalog.checklist(any())).thenReturn(null);
        assertThatThrownBy(()->service.create(OWNER,createRequest())).hasMessage("CHECKLIST_NOT_READY");
        when(catalog.lesson("lesson")).thenReturn(lesson(1));
        assertThatThrownBy(()->service.create(OWNER,createRequest())).hasMessage("INVALID_REQUEST");
        verify(store,never()).create(any(),anyInt());
    }
    @Test void capabilityDoesNotCallStorageOrModels() {
        when(catalog.checklist(any())).thenReturn(null);
        assertThat(service.capabilities("lesson").getList("lessonIds",String.class)).isEmpty();
        properties.setGradingEnabled(false);
        assertThat(service.capabilities("lesson").getBoolean("ready")).isFalse(); verifyNoInteractions(storage,store);
    }
    @Test void submitFreezesOnceAndQueuesDurably() {
        var s=submission(); when(store.owned(OWNER,ID)).thenReturn(s); when(store.beginFreeze(s,NOW)).thenReturn(s); when(storage.freeze(s)).thenReturn(s.audio);
        var queued=submission(); queued.status="queued"; when(store.queue(s,s.audio,NOW)).thenReturn(queued);
        assertThat(service.submit(OWNER,ID).status()).isEqualTo("queued");
        when(store.owned(OWNER,ID)).thenReturn(queued);
        assertThat(service.submit(OWNER,ID).status()).isEqualTo("queued"); verify(storage,times(1)).freeze(any());
    }
    @Test void uploadRefusesCompletedJob() {
        var s=submission(); s.status="completed"; when(store.owned(OWNER,ID)).thenReturn(s);
        assertThatThrownBy(()->service.upload(OWNER,ID,new Upload("audio/mp4",100))).hasMessage("UPLOAD_CLOSED"); verifyNoInteractions(storage);
    }
    @Test void explicitRetryUsesSameAudioAndIncrementIsCompareAndSet() {
        var s=submission(); s.status="failed"; s.error=new LearningModels.Error("PROCESSING_FAILED","failed",true);
        when(store.owned(OWNER,ID)).thenReturn(s);
        var next=submission(); next.attempt=2; next.status="queued"; when(store.retry(s,NOW,60)).thenReturn(next);
        assertThat(service.retry(OWNER,ID).status()).isEqualTo("queued"); verify(store).retry(s,NOW,60); verifyNoInteractions(storage);
    }
    @Test void retryCapAndExpiredReceiptsAreNotResubmitted() {
        var s=submission(); s.status="failed"; s.error=new LearningModels.Error("FAIL","failed",true); s.attempt=3;
        when(store.owned(OWNER,ID)).thenReturn(s);
        assertThatThrownBy(()->service.retry(OWNER,ID)).hasMessage("RETRY_NOT_ALLOWED");
        s.attempt=1; s.queuedAt=NOW.minusSeconds(86401);
        assertThatThrownBy(()->service.retry(OWNER,ID)).hasMessage("SUBMISSION_EXPIRED"); verify(store,never()).retry(any(),any(),anyInt());
    }
    @Test void partOneCompletionNeedsNoSubmission() {
        when(catalog.lesson("lesson")).thenReturn(lesson(1));
        assertThat(service.complete(OWNER,"lesson",new Completion("common","test-v1",null)).getBoolean("completed")).isTrue();
        verify(store).complete(OWNER,"lesson","common","test-v1",null,NOW); verify(store,never()).owned(any(),any());
    }
    @Test void completionRequiresExactSuccessfulOwnedSubmissionButNotPerfectScore() {
        var s=submission(); s.status="completed"; s.result=new Document("metCount",0); when(store.owned(OWNER,ID)).thenReturn(s);
        assertThat(service.complete(OWNER,"lesson",new Completion("IH","test-v1",ID)).getBoolean("completed")).isTrue();
        assertThatThrownBy(()->service.complete(OWNER,"lesson",new Completion("AL","test-v1",ID))).hasMessage("COMPLETION_NOT_READY");
        s.status="failed";
        assertThatThrownBy(()->service.complete(OWNER,"lesson",new Completion("IH","test-v1",ID))).hasMessage("COMPLETION_NOT_READY");
    }
    @Test void invalidUuidCannotBecomeStoragePath() { assertThatThrownBy(()->service.get(OWNER,"../../other")).hasMessage("INVALID_REQUEST"); }
}
