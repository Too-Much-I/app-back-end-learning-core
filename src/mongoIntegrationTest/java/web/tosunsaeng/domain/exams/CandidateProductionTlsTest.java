package web.tosunsaeng.domain.exams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Production profile, ephemeral keys/data only. No published ports or external network route. */
@EnabledIfEnvironmentVariable(named="LC_PROCESS_REHEARSAL", matches="true")
class CandidateProductionTlsTest {
    @TempDir Path temporary;
    static final String IMAGE="lc-prod-candidate:20261018-amd64-local";
    static final String ROUTE="/internal/v1/entitlements/access/events";
    static final ObjectMapper JSON=new ObjectMapper();

    @Test @Timeout(240)
    void productionPaidAppStartsAndAuthenticatesRealTlsEvents() throws Exception {
        assertThat(org.testcontainers.DockerClientFactory.instance().client().inspectImageCmd(IMAGE).exec().getId())
                .isEqualTo("sha256:c84d9216a2f9aeb9eb6f626fb37cfb4460f0b907056baa7284177c8106154cac");
        var billing=new RSAKeyGenerator(2048).keyID("rehearsal-billing").generate();
        var sender=new RSAKeyGenerator(2048).keyID("rehearsal-lc").generate();
        var pub=billing.toPublicJWK().toJSONObject(); pub.put("alg","RS256"); pub.put("use","sig"); pub.put("key_ops",List.of("verify"));
        var priv=sender.toJSONObject(); priv.put("alg","RS256"); priv.put("use","sig"); priv.put("key_ops",List.of("sign"));
        Path p12=temporary.resolve("fixture.p12"); String password="rehearsal-only";
        var keytool=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool").toString(),
                "-genkeypair","-alias","fixture","-keyalg","RSA","-keysize","2048","-validity","2",
                "-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-storetype","PKCS12",
                "-keystore",p12.toString(),"-storepass",password,"-noprompt").redirectErrorStream(true).start();
        keytool.getInputStream().readAllBytes(); assertThat(keytool.waitFor()).isZero();
        var ks=KeyStore.getInstance("PKCS12"); try(var in=Files.newInputStream(p12)){ks.load(in,password.toCharArray());}
        String pem="-----BEGIN CERTIFICATE-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(ks.getCertificate("fixture").getEncoded())+"\n-----END CERTIFICATE-----\n";
        String hash=emptyManifestHash();
        try(Network network=Network.builder().createNetworkCmdModifier(c->c.withInternal(true)).build();
            GenericContainer<?> mongo=new GenericContainer<>(DockerImageName.parse("mongo:7.0.14"))
                    .withNetwork(network).withNetworkAliases("mongo").withCommand("--replSet","rehearsal","--bind_ip_all")
                    .waitingFor(Wait.forLogMessage(".*Waiting for connections.*",1));
            GenericContainer<?> redis=new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withNetwork(network).withNetworkAliases("redis")) {
            mongo.start();redis.start();
            mongo(mongo,"rs.initiate({_id:'rehearsal',members:[{_id:0,host:'mongo:27017'}]}); for(let i=0;i<100&&!db.hello().isWritablePrimary;i++)sleep(100); if(!db.hello().isWritablePrimary)throw Error('primary');");
            mongo(mongo,indexes()+"db.exam_paid_rollout_state.insertOne({_id:'legacy-v1',status:'SEALED',environment:'production',database:'lc_process_rehearsal',revision:'prod-rehearsal-1',sha256:'"+hash+"',count:NumberInt(0),sealedAt:new Date(),writersDrained:true,readerCoverageVerified:true});");
            String props="app.billing.workload-jwt.private-jwk="+JSON.writeValueAsString(priv)+"\n"
                    +"app.billing.access-events.billing-public-jwk-set="+JSON.writeValueAsString(Map.of("keys",List.of(pub)))+"\n";
            try(GenericContainer<?> app=new GenericContainer<>(DockerImageName.parse(IMAGE)).withNetwork(network)
                    .withCopyToContainer(Transferable.of(props,0444),"/tmp/rehearsal.properties")
                    .withCopyToContainer(Transferable.of(Files.readAllBytes(p12),0444),"/tmp/fixture.p12")
                    .withCopyToContainer(Transferable.of(pem,0444),"/tmp/fixture-ca.pem")
                    .withCommand("java","-jar","/app/app.jar","--spring.config.additional-location=file:/tmp/rehearsal.properties")
                    .withEnv(environment(hash,password))
                    .waitingFor(Wait.forLogMessage(".*Started .* in .*seconds.*",1)).withStartupTimeout(Duration.ofSeconds(90))) {
                app.start();
                assertThat(request(app,"http://127.0.0.1:8080/actuator/health",false)).isEqualTo(200);
                assertThat(request(app,"http://127.0.0.1:8080"+ROUTE,false)).isEqualTo(404);
                assertThat(request(app,"https://localhost:8444"+ROUTE,false)).isEqualTo(401);
                assertThat(request(app,"https://localhost:8444/api/v1/exams",false)).isEqualTo(404);
                String body="{\"eventId\":\"11111111-1111-4111-8111-111111111111\",\"eventType\":\"AttemptGroupAccessRevoked\",\"schemaVersion\":1,\"producer\":\"billing\",\"consumerScopeId\":\"rehearsal-prod\",\"occurredAt\":\"2026-10-18T00:00:00.000Z\",\"userId\":\"00000000-0000-0000-0000-000000000001\",\"attemptGroupId\":\"22222222-2222-4222-8222-222222222222\",\"reason\":\"PURCHASE_REFUNDED\",\"confirmedSessionId\":null}";
                app.copyFileToContainer(Transferable.of(body,0444),"/tmp/event.json");
                for(String audience:List.of("wrong-audience","tosunsaeng-learning-core:entitlement-access-events:production","tosunsaeng-learning-core:entitlement-access-events:production")) {
                    long now=Instant.now().getEpochSecond();
                    var token=new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(billing.getKeyID()).build(),
                            new Payload(JSON.writeValueAsString(Map.of("iss","urn:tosunsaeng:workload:billing:production","sub","billing-service","aud",audience,"jti",UUID.randomUUID().toString(),"iat",now,"exp",now+60))));
                    token.sign(new RSASSASigner(billing));
                    app.copyFileToContainer(Transferable.of("Authorization: Bearer "+token.serialize(),0444),"/tmp/header.txt");
                    assertThat(request(app,"https://localhost:8444"+ROUTE,true)).isEqualTo(audience.equals("wrong-audience")?403:204);
                }
                assertThat(mongo(mongo,"print(db.billing_access_revocations.countDocuments())").trim()).isEqualTo("1");
                assertThat(mongo(mongo,"print(db.exam_sessions.countDocuments())").trim()).isEqualTo("0");
            }
        }
    }

    static Map<String,String> environment(String hash,String password) {
        var e=new HashMap<String,String>();
        e.put("SPRING_PROFILES_ACTIVE","prod");e.put("APP_DEPLOYMENT_ENVIRONMENT","prod");e.put("APP_AUTH_MODE","jwt");
        e.put("IDENTITY_ISSUER","https://identity.invalid");e.put("IDENTITY_JWK_SET_URI","https://identity.invalid/jwks");e.put("IDENTITY_AUDIENCE","rehearsal-lc");
        e.put("MONGODB_URI","mongodb://mongo:27017/lc_process_rehearsal?directConnection=true");e.put("MONGODB_DATABASE","lc_process_rehearsal");
        e.put("REDIS_HOST","redis");e.put("AWS_S3_BUCKET_NAME","rehearsal-not-real");e.put("AWS_EC2_METADATA_DISABLED","true");e.put("AI_SERVER_URL","http://127.0.0.1:9");
        for(String k:List.of("BILLING_PAID_EXAM_ENABLED","BILLING_PAID_EXAM_PRODUCTION_ACTIVATION_APPROVED","BILLING_CREATION_SAGA_ENABLED","BILLING_WORKLOAD_JWT_SENDER_ENABLED","BILLING_ACCESS_EVENTS_RECEIVER_ENABLED","USER_MERGED_WRITER_ENABLED","BILLING_LEGACY_EXAM_CREATE_ENABLED"))e.put(k,"true");
        for(String k:List.of("BILLING_WORKLOAD_JWT_ENVIRONMENT","BILLING_ACCESS_EVENTS_ENVIRONMENT","BILLING_PAID_EXAM_LEGACY_ENVIRONMENT"))e.put(k,"production");
        e.put("BILLING_BASE_URL","https://billing.invalid");e.put("BILLING_ACCESS_EVENTS_CONSUMER_SCOPE_ID","rehearsal-prod");
        e.put("BILLING_ACCESS_EVENTS_PORT","8444");e.put("BILLING_ACCESS_EVENTS_TLS_KEY_STORE","/tmp/fixture.p12");e.put("BILLING_ACCESS_EVENTS_TLS_KEY_STORE_PASSWORD",password);e.put("BILLING_ACCESS_EVENTS_TLS_KEY_ALIAS","fixture");
        e.put("BILLING_PAID_EXAM_LEGACY_DATABASE","lc_process_rehearsal");e.put("BILLING_PAID_EXAM_LEGACY_REVISION","prod-rehearsal-1");e.put("BILLING_PAID_EXAM_LEGACY_SHA256",hash);e.put("BILLING_PAID_EXAM_LEGACY_COUNT","0");return e;
    }
    static String emptyManifestHash() throws Exception {
        var d=MessageDigest.getInstance("SHA-256");
        for(String s:List.of("lc-legacy-manifest-v1","production","lc_process_rehearsal","prod-rehearsal-1")){byte[] b=s.getBytes(StandardCharsets.UTF_8);d.update(ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}d.update(new byte[4]);return HexFormat.of().formatHex(d.digest());
    }
    static String mongo(GenericContainer<?> mongo,String script) throws Exception {
        var r=mongo.execInContainer("mongosh","--quiet","lc_process_rehearsal","--eval",script);assertThat(r.getExitCode()).isZero();return r.getStdout();
    }
    static int request(GenericContainer<?> app,String url,boolean signed) throws Exception {
        var args=new ArrayList<>(List.of("curl","--silent","--max-time","15","--cacert","/tmp/fixture-ca.pem","-o","/dev/null","-w","%{http_code}"));
        if(signed)args.addAll(List.of("-H","@/tmp/header.txt","-H","Content-Type: application/json","--data-binary","@/tmp/event.json"));
        args.add(url);var r=app.execInContainer(args.toArray(String[]::new));assertThat(r.getExitCode()).isZero();return Integer.parseInt(r.getStdout());
    }
    static String indexes() {
        return """
            for(const c of ['exam_sessions','exam_creation_operations','mock_exams','exam_results','exam_summaries','user_ownership_guards','user_merged_inbox_events','exam_legacy_manifest_entries','exam_paid_rollout_state','billing_access_revocations']) db.createCollection(c);
            db.exam_creation_operations.createIndex({userId:1,operationId:1},{name:'uniq_exam_creation_user_operation',unique:true});
            db.exam_creation_operations.createIndex({userId:1,activeGuard:1},{name:'uniq_exam_creation_active_user',unique:true,partialFilterExpression:{activeGuard:true}});
            db.exam_creation_operations.createIndex({state:1,updatedAt:1},{name:'idx_exam_creation_state_updated'});
            db.exam_creation_operations.createIndex({purgeAt:1},{name:'ttl_exam_creation_purge',expireAfterSeconds:0,partialFilterExpression:{purgeAt:{$type:'date'}}});
            db.exam_sessions.createIndex({userId:1,creationOperationId:1},{name:'uniq_exam_sessions_creation_operation',unique:true,partialFilterExpression:{creationOperationId:{$type:'string'}}});
            db.exam_sessions.createIndex({billingReservationId:1},{name:'uniq_exam_sessions_billing_reservation',unique:true,partialFilterExpression:{billingReservationId:{$type:'string'}}});
            db.exam_sessions.createIndex({attemptGroupId:1,createdAt:1},{name:'idx_exam_sessions_attempt_group_created'});
            db.exam_sessions.createIndex({userId:1},{name:'uniq_exam_sessions_active_user',unique:true,partialFilterExpression:{active:true}});
            db.mock_exams.createIndex({mock_exam_id:1},{name:'uniq_mock_exams_mock_exam_id',unique:true});
            db.exam_results.createIndex({userId:1},{name:'idx_exam_results_user'});db.exam_summaries.createIndex({userId:1},{name:'idx_exam_summaries_user'});
            db.billing_access_revocations.createIndex({environment:1,producer:1,consumerScopeId:1,attemptGroupId:1},{name:'uq_paid_revoke_group',unique:true});
            db.billing_access_revocations.createIndex({environment:1,producer:1,consumerScopeId:1,eventId:1},{name:'uq_paid_revoke_event',unique:true});
            """;
    }
}
