package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;

/** Trusted manifest scope, never populated from client input. */
public record LearningMediaSource(String datasetId, String contentVersion, String bucket, String prefix) {
    static LearningMediaSource current(LearningProperties config) {
        return new LearningMediaSource(config.getDatasetId(),config.getContentVersion(),config.getBucket(),
                "answer-learning/"+config.getDatasetId()+"/"+config.getContentVersion());
    }
    static LearningMediaSource parse(Document source, LearningProperties config) {
        try {
            String dataset=source.getString("datasetId"), version=source.getString("contentVersion");
            String bucket=source.getString("bucket"), prefix=source.getString("prefix");
            if (dataset==null || !dataset.matches("[A-Za-z0-9_-]+")
                    || version==null || !version.matches("[A-Za-z0-9_-]+")
                    || !config.getBucket().equals(bucket)
                    || !("answer-learning/"+dataset+"/"+version).equals(prefix))
                throw LearningFailure.unavailable();
            return new LearningMediaSource(dataset,version,bucket,prefix);
        } catch (RuntimeException invalid) { throw LearningFailure.unavailable(); }
    }
}
