package web.tosunsaeng.domain.learningrecorddeletion.tools;

import com.mongodb.*;
import com.mongodb.client.MongoClients;
import org.springframework.core.env.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import web.tosunsaeng.domain.exams.billing.*;
import web.tosunsaeng.domain.learningrecorddeletion.application.*;
import web.tosunsaeng.domain.learningrecorddeletion.config.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.*;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.BillingDeletionClient;
import web.tosunsaeng.domain.usermerge.application.UserOwnershipGuardService;
import web.tosunsaeng.domain.usermerge.repository.UserOwnershipGuardRepository;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Separate main, not a Spring bean. No scheduler, target creation, cleanup, or rollout writes. */
public final class DeletionDeliveryPreflight {
    private DeletionDeliveryPreflight() { }

    public static void main(String[] args) {
        // Driver diagnostics can contain hosts/commands. Only fixed result codes may leave this process.
        ((ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        try {
            if (args.length != 0) throw new IllegalArgumentException("Use injected configuration");
            System.out.println(run(System.getenv()));
        } catch (Exception failure) {
            System.err.println("PREFLIGHT_FAILED_OR_OUTCOME_UNKNOWN");
            // Never blindly resend: a remote commit or local ACK commit may already have happened.
            System.exit(1);
        }
    }

    static String run(Map<String, String> values) {
        boolean send = sendMode(values);
        var selection = new BillingDeletionSender.ExactTarget(required(values, "LC_PREFLIGHT_TARGET_ID"),
                required(values, "LC_PREFLIGHT_USER_ID"), required(values, "LC_PREFLIGHT_EVENT_ID"),
                required(values, "LC_PREFLIGHT_PAYLOAD_SHA256"));
        var connection = new ConnectionString(required(values, "MONGODB_URI"));
        requireProductionDatabase(connection);
        var settings = MongoClientSettings.builder().applyConnectionString(connection)
                .readConcern(ReadConcern.MAJORITY).writeConcern(WriteConcern.MAJORITY)
                .readPreference(ReadPreference.primary())
                .applyToClusterSettings(b -> b.serverSelectionTimeout(10, TimeUnit.SECONDS)).build();
        try (var client = MongoClients.create(settings)) {
            var mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "to-teacher-app"));
            var env = new StandardEnvironment();
            env.getPropertySources().addFirst(new MapPropertySource("preflight", Map.of(
                    "app.deployment.environment", "prod", "app.auth.mode", "jwt",
                    "app.learning-record-deletion.command-enabled", true,
                    "app.learning-record-deletion.aggregate-enabled", false)));
            // All normal storage/index/drain gates remain required. Only delivery proof is produced here.
            new DeletionStartupValidator(mongo, env).afterPropertiesSet();
            BillingDeletionConfiguration.verifyDeliveryStorage(mongo);
            var target = mongo.findById(selection.targetId(), DeletionTarget.class);
            requireTarget(selection, target);
            if (!send) return "PREFLIGHT_READ_ONLY_" + target.getBillingDelivery().getState().name();

            var clock = Clock.systemUTC();
            var jwt = new WorkloadJwtProperties();
            jwt.setSenderEnabled(true); jwt.setEnvironment("production");
            jwt.setPrivateJwk(required(values, "BILLING_WORKLOAD_JWT_PRIVATE_JWK"));
            var remote = new BillingDeletionClient("https://billing-workload-prod.to-teacher.com",
                    new BillingWorkloadTokenIssuer(jwt, clock), clock);
            var guards = new UserOwnershipGuardService(mongo,
                    new MongoRepositoryFactory(mongo).getRepository(UserOwnershipGuardRepository.class));
            new BillingDeletionSender(mongo, new DeletionTransactions(mongo.getMongoDatabaseFactory()),
                    guards, remote, clock, "production").sendExact(selection);
            var after = mongo.findById(selection.targetId(), DeletionTarget.class);
            requireTarget(selection, after);
            return "PREFLIGHT_STORED_" + after.getBillingDelivery().getState().name();
        }
    }

    static boolean sendMode(Map<String, String> values) {
        String mode = values.getOrDefault("LC_PREFLIGHT_MODE", "inspect");
        if ("inspect".equals(mode)) return false;
        if (!"send-once".equals(mode)
                || !"SEND_EXACT_APPROVED_TARGET".equals(values.get("LC_PREFLIGHT_APPROVAL"))
                || !"true".equals(values.get("LC_PREFLIGHT_OTHER_WRITERS_DRAINED")))
            throw new IllegalArgumentException("Explicit preflight approval and drain evidence required");
        return true;
    }

    static void requireProductionDatabase(ConnectionString c) {
        if (!c.isSrvProtocol() || !c.getHosts().equals(java.util.List.of("cluster0.nuuovhj.mongodb.net"))
                || !"to-teacher-app".equals(c.getDatabase()))
            throw new IllegalArgumentException("Unexpected preflight database");
    }

    private static void requireTarget(BillingDeletionSender.ExactTarget expected, DeletionTarget target) {
        if (!expected.matches(target) || !"production".equals(target.getBillingDelivery().getEnvironment()))
            throw new IllegalStateException("Preflight evidence mismatch");
    }
    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing preflight configuration");
        return value;
    }
}
