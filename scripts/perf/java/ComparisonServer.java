package benchmark;

import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.springframework.context.annotation.*;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import java.lang.reflect.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Benchmark-only composition root. No product configs, credentials or component scan. */
public class ComparisonServer {
    static final String ROOT = "web.tosunsaeng.";
    static final String USER = "00000000-0000-0000-0000-000000000001";
    static final Map<Class<?>,Object> beans = new HashMap<>();
    static final List<Integer> busySamples = new CopyOnWriteArrayList<>();
    static volatile String samplerError;
    static MongoTemplate mongo;
    static MongoRepositoryFactory repositories;
    static boolean app;
    static AnnotationConfigServletWebServerApplicationContext context;

    @Configuration @EnableWebMvc
    static class WebConfig {
        @Bean TomcatServletWebServerFactory webServerFactory() throws Exception {
            var factory = new TomcatServletWebServerFactory(0);
            factory.setAddress(InetAddress.getLoopbackAddress());
            factory.addConnectorCustomizers(c -> {
                c.setProperty("maxThreads", "20"); c.setProperty("minSpareThreads", "20");
                c.setProperty("acceptCount", "100");
            });
            return factory;
        }
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
        @Bean ServletRegistrationBean<DispatcherServlet> dispatcherRegistration(DispatcherServlet servlet) {
            return new ServletRegistrationBean<>(servlet, "/");
        }
    }

    @RestController
    static class Control {
        @PostMapping("/__bench/seed") Map<String,Object> seed(@RequestBody List<String> ids) {
            for (String id : ids) {
                if (!id.startsWith("ex_bench_")) throw new IllegalArgumentException("fixture IDs only");
                if (app) mongo.getCollection("exam_sessions").insertOne(new Document("_id",id)
                        .append("userId",USER).append("mockExamId","mock_exam_001")
                        .append("active",true).append("status","IN_PROGRESS").append("cycleNumber",1));
            }
            busySamples.clear();
            return Map.of("seeded",ids.size());
        }
        @GetMapping("/__bench/metrics") Map<String,Object> metrics() {
            return Map.of("busy",List.copyOf(busySamples),"samplerError",samplerError==null?"":samplerError);
        }
    }

    public static void main(String[] args) throws Exception {
        app = args[0].equals("app");
        int aiPort = Integer.parseInt(args[1]);
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("ROOT")).setLevel(ch.qos.logback.classic.Level.ERROR);
        var mongoContainer = new MongoDBContainer("mongo:7.0.14")
                .withCreateContainerCmdModifier(c -> c.getHostConfig().withNanoCPUs(1_000_000_000L).withMemory(536870912L));
        var redisContainer = new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379)
                .withCreateContainerCmdModifier(c -> c.getHostConfig().withNanoCPUs(500_000_000L).withMemory(134217728L));
        mongoContainer.start(); redisContainer.start();
        var mongoClient = MongoClients.create(mongoContainer.getReplicaSetUrl());
        mongo = new MongoTemplate(mongoClient,"isolated_poc_comparison");
        repositories = new MongoRepositoryFactory(mongo);
        beans.put(MongoTemplate.class,mongo); beans.put(Clock.class,Clock.systemUTC());
        // Common lookup indexes, fixed for both variants. Not a reproduction of production index inventories.
        mongo.getCollection("exam_results").createIndex(new Document("examId",1));
        mongo.getCollection("exam_summaries").createIndex(new Document("examId",1));
        mongo.getCollection("question_grading_jobs").createIndex(new Document("examId",1));
        var redisConnection = new LettuceConnectionFactory(redisContainer.getHost(),redisContainer.getMappedPort(6379));
        redisConnection.afterPropertiesSet(); redisConnection.start();
        var redis = new RedisTemplate<String,Object>(); redis.setConnectionFactory(redisConnection);
        redis.setKeySerializer(new StringRedisSerializer()); redis.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redis.afterPropertiesSet(); beans.put(RedisTemplate.class,redis);
        var httpFactory = new SimpleClientHttpRequestFactory();
        httpFactory.setConnectTimeout(1000); httpFactory.setReadTimeout(5000);
        var rest = new RestTemplate(httpFactory);
        rest.setInterceptors(List.of((request,body,execution) -> {
            URI uri=request.getURI();
            if (uri.getPath().equals("/evaluations") && (uri.getHost().equals("ai-server") || uri.getHost().equals("127.0.0.1"))) {
                return execution.execute(new HttpRequestWrapper(request) {
                    public URI getURI() { return URI.create("http://127.0.0.1:"+aiPort+"/evaluations"); }
                }, body);
            }
            if (uri.getHost().equals("127.0.0.1") && uri.getPort()==aiPort && uri.getPath().equals("/audio"))
                return execution.execute(request,body);
            throw new IllegalStateException("non-fixture network request denied");
        }));
        beans.put(RestTemplate.class,rest);
        var signer=mock(S3Presigner.class);
        var signed=mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(new URL("http://127.0.0.1:"+aiPort+"/audio"));
        when(signer.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
        beans.put(S3Presigner.class,signer); beans.put(S3Client.class,mock(S3Client.class));
        var summaryExecutor=new ThreadPoolTaskExecutor(); summaryExecutor.setCorePoolSize(2); summaryExecutor.setMaxPoolSize(2);
        summaryExecutor.setQueueCapacity(100); summaryExecutor.initialize(); beans.put(TaskExecutor.class,summaryExecutor);
        if(app) {
            Class<?> props=Class.forName(ROOT+"global.config.GradingProperties");
            beans.put(props,props.getConstructors()[0].newInstance(Duration.ofMinutes(1),Duration.ofMinutes(3),3,
                    URI.create("http://127.0.0.1:"+aiPort),Duration.ofSeconds(1),Duration.ofSeconds(5),2,100));
            Class<?> user=Class.forName(ROOT+"global.auth.CurrentUserProvider");
            beans.put(user,java.lang.reflect.Proxy.newProxyInstance(user.getClassLoader(),new Class[]{user},(p,m,a)->USER));
        }
        List<Document> questions=new ArrayList<>();
        for(int q=1;q<=11;q++) questions.add(new Document("question_number",q).append("part_number",part(q)).append("question","Benchmark fixture"));
        mongo.getCollection("mock_exams").insertOne(new Document("mock_exam_id","mock_exam_001").append("title","Benchmark fixture").append("questions",questions));
        Object service=resolve(Class.forName(ROOT+"domain.exams.application.ExamServiceImpl"));
        beans.put(Class.forName(ROOT+"domain.exams.application.ExamService"),service);
        Class controllerClass=Class.forName(ROOT+"domain.exams.api.ExamRestController");
        Object controller=resolve(controllerClass);
        context=new AnnotationConfigServletWebServerApplicationContext();
        context.register(WebConfig.class); context.registerBean("control",Control.class,Control::new);
        context.registerBean("examController",controllerClass,()->controller); context.refresh();
        var protocol=(org.apache.coyote.AbstractProtocol<?>)((TomcatWebServer)context.getWebServer()).getTomcat().getConnector().getProtocolHandler();
        var sampler=Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(()->{
            try {
                Object executor=protocol.getExecutor();
                busySamples.add(((Number)executor.getClass().getMethod("getActiveCount").invoke(executor)).intValue());
            } catch(Exception e) { samplerError=e.toString(); }
        },0,20,TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            sampler.shutdownNow(); context.close(); summaryExecutor.shutdown(); redisConnection.destroy(); mongoClient.close();
            redisContainer.stop(); mongoContainer.stop();
        }));
        System.out.println("BENCH_READY "+context.getWebServer().getPort());
    }

    static int part(int q) { return q<=2?1:q<=4?2:q<=7?3:q<=10?4:5; }
    static Object resolve(Class<?> type) throws Exception {
        if(beans.containsKey(type)) return beans.get(type);
        Object value;
        if(MongoRepository.class.isAssignableFrom(type)) value=repositories.getRepository(type);
        else if(type.getSimpleName().equals("BillingSagaProperties")) value=type.getConstructor().newInstance();
        else if(Set.of("BillingExamCreationSaga","ExamReadService","ModelAnswerCatalogService").contains(type.getSimpleName())) value=mock(type);
        else {
            Constructor<?> ctor=Arrays.stream(type.getConstructors()).max(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
            Object[] params=new Object[ctor.getParameterCount()];
            for(int i=0;i<params.length;i++) params[i]=resolve(ctor.getParameterTypes()[i]);
            value=ctor.newInstance(params);
        }
        for(Field field:type.getDeclaredFields()) if(field.getName().equals("bucketName")) ReflectionTestUtils.setField(value,"bucketName","benchmark-only");
        beans.put(type,value); return value;
    }
}
