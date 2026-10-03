package web.tosunsaeng.domain.learningrecorddeletion.analytics;

import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveCallback;

/** Entity callback is synchronous (unlike configurable asynchronous Mongo application events). */
public final class LearningActivityMongoCallback implements BeforeSaveCallback<Object> {
    private final java.util.function.Supplier<LearningActivityRecorder> recorderProvider;
    public LearningActivityMongoCallback(LearningActivityRecorder recorder) { this(() -> recorder); }
    public LearningActivityMongoCallback(java.util.function.Supplier<LearningActivityRecorder> recorder) { this.recorderProvider = recorder; }
    @Override public Object onBeforeSave(Object entity, Document document, String collection) {
        if (LearningActivityRecorder.SOURCES.contains(collection)) {
            LearningActivityRecorder recorder = recorderProvider.get();
            recorder.transition(collection, recorder.source(collection, document.get("_id")), document);
        }
        return entity;
    }
}
