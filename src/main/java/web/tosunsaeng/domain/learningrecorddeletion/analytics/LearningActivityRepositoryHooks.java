package web.tosunsaeng.domain.learningrecorddeletion.analytics;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.mongodb.repository.Update;
import web.tosunsaeng.domain.exams.domain.repository.*;

/** Explicitly covers repository CAS updates, which do not invoke Mongo entity callbacks. */
public final class LearningActivityRepositoryHooks implements BeanPostProcessor {
    private final ObjectProvider<LearningActivityRecorder> recorder;
    private final ObjectProvider<web.tosunsaeng.domain.learningrecorddeletion.infrastructure.DeletionPersistenceFence> fence;
    private final ObjectProvider<org.springframework.data.mongodb.core.MongoTemplate> mongo;
    public LearningActivityRepositoryHooks(ObjectProvider<LearningActivityRecorder> recorder,
            ObjectProvider<web.tosunsaeng.domain.learningrecorddeletion.infrastructure.DeletionPersistenceFence> fence,
            ObjectProvider<org.springframework.data.mongodb.core.MongoTemplate> mongo) {
        this.recorder = recorder; this.fence = fence; this.mongo = mongo;
    }
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        String collection = bean instanceof ExamSessionRepository ? "exam_sessions"
                : bean instanceof QuestionGradingJobRepository ? "question_grading_jobs"
                : bean instanceof SummaryGradingJobRepository ? "summary_grading_jobs" : null;
        if (collection == null) return bean;
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.addAdvice((MethodInterceptor) call -> {
            // Custom recovery fragment performs a raw CAS too; it no longer carries Mongo @Update.
            boolean recovery = bean instanceof QuestionGradingJobRepository
                    && call.getMethod().getName().equals("reopenCompletedMissingResult")
                    && java.util.Arrays.equals(call.getMethod().getParameterTypes(),
                            new Class<?>[] {String.class, int.class, java.time.Instant.class});
            if (!recovery && !call.getMethod().isAnnotationPresent(Update.class)) return call.proceed();
            LearningActivityRecorder sink = recorder.getIfAvailable();
            Object id = call.getArguments()[0];
            var before = java.util.Objects.requireNonNull(mongo.getIfAvailable()).findById(id, org.bson.Document.class, collection);
            if (collection.equals("exam_sessions") && (call.getMethod().getName().equals("confirmEntitlementIfConfirming")
                    || call.getMethod().getName().equals("abandonIfEntitlementConfirming")))
                java.util.Objects.requireNonNull(fence.getIfAvailable()).checkReservationFinalization(before);
            else java.util.Objects.requireNonNull(fence.getIfAvailable()).check(collection, before);
            Object result = call.proceed();
            if (sink != null && result instanceof Number n && n.longValue() > 0)
                sink.recordRawTransition(collection, before, sink.source(collection, id));
            return result;
        });
        return proxy.getProxy();
    }
}
