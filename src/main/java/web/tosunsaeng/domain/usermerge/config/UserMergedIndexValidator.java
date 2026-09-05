package web.tosunsaeng.domain.usermerge.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamSummary;
import web.tosunsaeng.domain.usermerge.domain.UserMergedInboxEvent;
import web.tosunsaeng.domain.usermerge.domain.UserOwnershipGuard;

import java.util.List;

@Component
@Profile("!test")
@ConditionalOnExpression("${app.user-merged.writer-enabled:false} || "
        + "${app.user-merged.consumer-enabled:false} || "
        + "${app.user-merged.source-deny-enabled:false}")
public class UserMergedIndexValidator implements ApplicationRunner {

    public static final String RESULT_OWNER_INDEX = "idx_exam_results_user";
    public static final String SUMMARY_OWNER_INDEX = "idx_exam_summaries_user";
    private final MongoTemplate mongoTemplate;

    public UserMergedIndexValidator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireIdIndex(UserOwnershipGuard.class);
        requireIdIndex(UserMergedInboxEvent.class);
        requireOwnerIndex(ExamResult.class, RESULT_OWNER_INDEX);
        requireOwnerIndex(ExamSummary.class, SUMMARY_OWNER_INDEX);
    }

    private void requireIdIndex(Class<?> entityType) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(entityType).getIndexInfo();
        boolean valid = indexes.stream().anyMatch(index -> "_id_".equals(index.getName())
                && index.isUnique()
                && index.getIndexFields().size() == 1
                && "_id".equals(index.getIndexFields().getFirst().getKey()));
        if (!valid) {
            throw new IllegalStateException("Required UserMerged _id index is missing");
        }
    }

    private void requireOwnerIndex(Class<?> entityType, String name) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(entityType).getIndexInfo();
        boolean valid = indexes.stream().anyMatch(index -> name.equals(index.getName())
                && !index.isUnique()
                && !index.isSparse()
                && !index.isHidden()
                && index.getIndexFields().size() == 1
                && "userId".equals(index.getIndexFields().getFirst().getKey())
                && Sort.Direction.ASC == index.getIndexFields().getFirst().getDirection());
        if (!valid) {
            throw new IllegalStateException("Required UserMerged owner index is missing: " + name);
        }
    }
}
