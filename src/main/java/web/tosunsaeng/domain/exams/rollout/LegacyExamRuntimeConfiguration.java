package web.tosunsaeng.domain.exams.rollout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/** No installer or mutable registry. OFF does not parse inventory settings or query Mongo. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.billing.paid-exam.enabled", havingValue = "true")
public class LegacyExamRuntimeConfiguration {
    @Bean
    LegacyExamRuntime legacyExamRuntime(MongoTemplate mongo,
            @Value("${app.billing.workload-jwt.environment:}") String workloadEnvironment,
            @Value("${app.billing.paid-exam.legacy.environment:}") String environment,
            @Value("${app.billing.paid-exam.legacy.database:}") String database,
            @Value("${app.billing.paid-exam.legacy.revision:}") String revision,
            @Value("${app.billing.paid-exam.legacy.sha256:}") String sha256,
            @Value("${app.billing.paid-exam.legacy.count:-1}") int count) {
        if (environment.isBlank() || !environment.equals(workloadEnvironment)) throw LegacyExamManifest.invalid();
        var expected = new LegacyExamManifest.Summary(environment, database, revision, sha256, count);
        return new LegacyExamRuntime(new PaidExamCutoverInspector(mongo).verifySealed(expected));
    }
}
