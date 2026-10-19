package web.tosunsaeng.domain.exams;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import java.time.*;

import static org.assertj.core.api.Assertions.*;

/** Opt-in actual image restart rehearsal. Local profile, NOT production paid/TLS/ECS certification. */
@EnabledIfEnvironmentVariable(named = "LC_PROCESS_REHEARSAL", matches = "true")
class CandidateProcessRestartTest {
    private static final String IMAGE = "lc-prod-candidate:20261018-amd64-local";
    private static final String EXAM = "rehearsal-exam";

    @Test void actualImageRestartPreservesJobsAndAcceptsLateDuplicateCallbacks() throws Exception {
        assertThat(org.testcontainers.DockerClientFactory.instance().client().inspectImageCmd(IMAGE).exec().getId())
                .as("exact locally reviewed candidate; do not silently test a retagged image")
                .isEqualTo("sha256:c84d9216a2f9aeb9eb6f626fb37cfb4460f0b907056baa7284177c8106154cac");
        try (Network network = Network.builder().createNetworkCmdModifier(c -> c.withInternal(true)).build();
             GenericContainer<?> mongo = new GenericContainer<>(DockerImageName.parse("mongo:7.0.14"))
                     .withNetwork(network).withNetworkAliases("mongo").withCommand("--replSet", "rehearsal", "--bind_ip_all")
                     .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1));
             GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                     .withNetwork(network).withNetworkAliases("redis")) {
            mongo.start(); redis.start();
            mongo(mongo, "rs.initiate({_id:'rehearsal',members:[{_id:0,host:'mongo:27017'}]}); while(!db.hello().isWritablePrimary){sleep(100)}");
            {
                mongo(mongo, "db.exam_sessions.insertOne({_id:'rehearsal-exam',userId:'00000000-0000-0000-0000-000000000001',mockExamId:'rehearsal-mock',active:true,status:'IN_PROGRESS',createdAt:new Date()});"
                        + "db.mock_exams.insertOne({_id:'rehearsal-mock',mock_exam_id:'rehearsal-mock',active:true,questions:[{question_number:1,part_number:1}]});"
                        + "db.question_grading_jobs.insertOne({_id:'question:rehearsal-exam:1:0',examId:'rehearsal-exam',mockExamId:'rehearsal-mock',questionNumber:1,retryCount:0,status:'COMPLETED',activityUserSubmission:true,firstUserSubmittedAt:new Date(),completedAt:new Date()});"
                        + "db.exam_results.insertOne({_id:'rehearsal-result',examId:'rehearsal-exam',userId:'00000000-0000-0000-0000-000000000001',questionNumber:1,retryCount:0,score:3.0});"
                        + "db.summary_grading_jobs.insertOne({_id:'summary:rehearsal-exam:v1',examId:'rehearsal-exam',mockExamId:'rehearsal-mock',summaryVersion:1,generationAttempt:1,status:'PROCESSING',dispatchAttempt:1,pendingAt:new Date(),processingStartedAt:new Date(),version:NumberLong(0)});");
                String before = mongo(mongo, "print(EJSON.stringify(db.summary_grading_jobs.findOne()))");
                try (GenericContainer<?> first = app(network)) {
                    first.start();
                    assertThat(get(first, "/actuator/health")).isEqualTo(200);
                    // Real SIGTERM and process/container exit, no mock ApplicationContext replacement.
                    first.getDockerClient().stopContainerCmd(first.getContainerId()).withTimeout(30).exec();
                    var stopped = first.getDockerClient().inspectContainerCmd(first.getContainerId()).exec().getState();
                    assertThat(stopped.getRunning()).isFalse();
                    assertThat(stopped.getOOMKilled()).isFalse();
                    assertThat(stopped.getExitCodeLong()).isIn(0L, 143L);
                }
                assertThat(mongo(mongo, "print(EJSON.stringify(db.summary_grading_jobs.findOne()))")).isEqualTo(before);
                try (GenericContainer<?> second = app(network)) {
                    second.start();
                    String azure = "{\"metadata\":{\"user_id\":\"" + EXAM
                            + "\",\"question_number\":1,\"retry_count\":0},\"fixture\":true}";
                    assertThat(post(second, "/api/v1/exams/callback/azure", azure)).isEqualTo(200);
                    assertThat(post(second, "/api/v1/exams/callback/azure", azure)).isEqualTo(200);
                    assertThat(mongo(mongo, "print(db.azure_results.countDocuments({exam_id:'rehearsal-exam'}))").trim()).isEqualTo("1");
                    assertThat(mongo(mongo, "print(EJSON.stringify(db.summary_grading_jobs.findOne()))")).isEqualTo(before);
                    String summary = "{\"user_id\":\"" + EXAM + "\",\"generation_attempt\":1,"
                            + "\"suggested_total_score\":100,\"part_feedback\":{\"1\":\"fixture\"}}";
                    assertThat(post(second, "/api/v1/exams/callback/feedback", summary)).isEqualTo(200);
                    assertThat(post(second, "/api/v1/exams/callback/feedback", summary)).isEqualTo(200);
                    assertThat(mongo(mongo, "print(db.exam_summaries.countDocuments({examId:'rehearsal-exam'}))").trim()).isEqualTo("1");
                    assertThat(mongo(mongo, "print(db.summary_grading_jobs.findOne().status)").trim()).isEqualTo("COMPLETED");
                    assertThat(mongo(mongo, "print(db.exam_sessions.findOne().status)").trim()).isEqualTo("COMPLETED");
                }
            }
        }
    }

    private static String mongo(GenericContainer<?> mongo, String script) throws Exception {
        var result = mongo.execInContainer("mongosh", "--quiet", "lc_process_rehearsal", "--eval", script);
        assertThat(result.getExitCode()).as("isolated Mongo fixture command").isZero();
        return result.getStdout();
    }

    private static GenericContainer<?> app(Network network) {
        return new GenericContainer<>(DockerImageName.parse(IMAGE)).withNetwork(network)
                .withEnv("SPRING_PROFILES_ACTIVE", "local")
                .withEnv("MONGODB_URI", "mongodb://mongo:27017/lc_process_rehearsal?directConnection=true")
                .withEnv("MONGODB_DATABASE", "lc_process_rehearsal")
                .withEnv("REDIS_HOST", "redis").withEnv("AWS_S3_BUCKET_NAME", "rehearsal-not-real")
                .withEnv("AWS_EC2_METADATA_DISABLED", "true")
                .withEnv("AI_SERVER_URL", "http://127.0.0.1:9")
                .waitingFor(Wait.forLogMessage(".*Started .* in .*seconds.*", 1))
                .withStartupTimeout(Duration.ofSeconds(90));
    }

    private static int get(GenericContainer<?> app, String path) throws Exception {
        var r = app.execInContainer("curl", "--silent", "--max-time", "10", "-o", "/dev/null", "-w", "%{http_code}", "http://127.0.0.1:8080" + path);
        assertThat(r.getExitCode()).isZero(); return Integer.parseInt(r.getStdout());
    }
    private static int post(GenericContainer<?> app, String path, String body) throws Exception {
        var r = app.execInContainer("curl", "--silent", "--max-time", "15", "-o", "/dev/null", "-w", "%{http_code}",
                "-H", "Content-Type: application/json", "--data-binary", body, "http://127.0.0.1:8080" + path);
        assertThat(r.getExitCode()).isZero(); return Integer.parseInt(r.getStdout());
    }
}
