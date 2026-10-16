package web.tosunsaeng.domain.exams.rollout;

import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.exams.billing.BillingSagaIndexValidator;
import web.tosunsaeng.domain.usermerge.config.UserMergedIndexValidator;
import web.tosunsaeng.global.config.ExamAssignmentIndexValidator;

/** Read-only strict checks, including validators normally excluded/warning-only in the test profile. */
@Component
public final class PaidExamTestIndexCheck {
    private final MongoTemplate mongo;
    private final Environment environment;

    public PaidExamTestIndexCheck(MongoTemplate mongo, Environment environment) {
        this.mongo = mongo;
        this.environment = environment;
    }

    public void verify() {
        try {
            new BillingSagaIndexValidator(mongo, environment).validate(true);
            new ExamAssignmentIndexValidator(mongo, environment).validateRequiredIndexes();
            new UserMergedIndexValidator(mongo).run(null);
        } catch (RuntimeException failure) {
            // Do not propagate database connection details into startup error messages.
            throw new IllegalStateException("Paid exam test required index validation failed");
        }
    }
}
