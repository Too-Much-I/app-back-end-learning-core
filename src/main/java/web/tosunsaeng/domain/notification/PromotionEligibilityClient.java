package web.tosunsaeng.domain.notification;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.*;
import java.time.*;
import java.util.Map;
import web.tosunsaeng.domain.exams.billing.BillingWorkloadTokenIssuer;
import static web.tosunsaeng.domain.exams.billing.BillingWorkloadTokenIssuer.Purpose.*;

/** No user-token impersonation, redirects, positive cache, or retry. */
public class PromotionEligibilityClient implements AutoCloseable {
    private final URI billing,identity;
    private final BillingWorkloadTokenIssuer issuer;
    private final Clock clock;
    private final CloseableHttpClient http=HttpClients.custom().disableAutomaticRetries().disableRedirectHandling()
            .disableCookieManagement().setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(2000)
                    .setSocketTimeout(3000).setConnectionRequestTimeout(1000).build()).build();
    private final ScheduledExecutorService deadline=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"promotion-eligibility-deadline");t.setDaemon(true);return t;
    });
    private final ObjectMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public PromotionEligibilityClient(PromotionPushProperties p,BillingWorkloadTokenIssuer issuer,Clock clock){
        billing=endpoint(p.getBillingEndpoint(),"/internal/v1/eligibility/promotion/query");
        identity=endpoint(p.getIdentityEndpoint(),"/workload/v1/marketing-push/eligibility");
        this.issuer=issuer;this.clock=clock;
    }
    // Loopback-only seam; production endpoints remain HTTPS with exact paths.
    PromotionEligibilityClient(URI billing,URI identity,BillingWorkloadTokenIssuer issuer,Clock clock){
        for(URI uri:java.util.List.of(billing,identity))
            if(!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()))throw new IllegalArgumentException();
        this.billing=billing;this.identity=identity;this.issuer=issuer;this.clock=clock;
    }
    private static URI endpoint(String value,String path){
        URI uri=URI.create(value==null?"":value);
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null
                || uri.getFragment()!=null || !path.equals(uri.getRawPath()))throw new IllegalStateException("Invalid promotion eligibility endpoint");
        return uri;
    }
    public boolean eligible(String user){return query(billing,PROMOTION_ELIGIBILITY,user) && query(identity,MARKETING_PUSH_ELIGIBILITY,user);}
    private boolean query(URI uri,BillingWorkloadTokenIssuer.Purpose purpose,String user){
        HttpPost request=new HttpPost(uri);
        ScheduledFuture<?> timeout=null;
        try {
            request.setHeader("Authorization",issuer.authorization(purpose));
            request.setHeader("Content-Type","application/json");
            request.setEntity(new ByteArrayEntity(json.writeValueAsBytes(Map.of("userId",user))));
            // Abort bounds headers AND the response body, including a slow trickle.
            timeout=deadline.schedule(request::abort,3,TimeUnit.SECONDS);
            try(var response=http.execute(request)){
                if(response.getStatusLine().getStatusCode()!=200 || response.getEntity()==null)return false;
                byte[] bytes=response.getEntity().getContent().readNBytes(16385); if(bytes.length>16384)return false;
                JsonNode node=json.readTree(bytes);
                if(node==null || !node.isObject() || node.size()!=3 || !node.path("userId").isTextual()
                        || !user.equals(node.path("userId").textValue()) || !node.path("eligible").isBoolean()
                        || !node.path("checkedAt").isTextual())return false;
                Instant checked=Instant.parse(node.get("checkedAt").textValue()),now=clock.instant();
                return node.get("eligible").booleanValue() && !checked.isBefore(now.minusSeconds(10)) && !checked.isAfter(now.plusSeconds(30));
            }
        }catch(Exception unavailable){return false;}
        finally{request.abort();if(timeout!=null)timeout.cancel(false);}
    }
    @Override public void close() throws IOException {deadline.shutdownNow();http.close();}
}
