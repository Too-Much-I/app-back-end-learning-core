package web.tosunsaeng.domain.exams.billing;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static web.tosunsaeng.domain.exams.billing.BillingClientException.Category.*;
import static web.tosunsaeng.domain.exams.billing.PaidBillingErrorDecoder.Endpoint.*;

class PaidBillingErrorDecoderTest {
    final PaidBillingErrorDecoder decoder = new PaidBillingErrorDecoder(new BillingSagaConfiguration().billingContractObjectMapper());
    final String member = "{\"code\":\"MEMBER_EVIDENCE_PENDING\",\"message\":\"The operation is waiting for member evidence and request resumption.\",\"retryable\":true,\"correlationId\":\"opaque\"}";
    @Test void invalidHeaderAndUtf8FailClosedWithoutRawCause() {
        for (String header : List.of("01","1, 1","2","INVALID","0","-1"))
            assertThat(decoder.decode(RESERVE,503,member.getBytes(StandardCharsets.UTF_8),header).category()).isEqualTo(CONTRACT_ERROR);
        assertThat(decoder.decode(RESERVE,503,member.getBytes(StandardCharsets.UTF_8),null).category()).isEqualTo(CONTRACT_ERROR);
        var error = decoder.decode(RESERVE,503,new byte[]{(byte)0xc3,0x28},"1");
        assertThat(error.category()).isEqualTo(CONTRACT_ERROR); assertThat(error).hasNoCause();
        for (int status : List.of(401,403))
            assertThat(decoder.decode(RESERVE,status,new byte[]{(byte)0xc3},null).category()).isEqualTo(AUTH_FAILURE);
    }
    @Test void businessErrorsAreEndpointBoundAndNotServiceAuthentication() {
        String revoked = "{\"code\":\"ENTITLEMENT_REVOKED\",\"message\":\"Revoked\",\"retryable\":false,\"correlationId\":\"opaque\"}";
        assertThat(decoder.decode(CONFIRM,409,revoked.getBytes(StandardCharsets.UTF_8),null).category()).isEqualTo(ENTITLEMENT_REVOKED);
        assertThat(decoder.decode(RESERVE,409,revoked.getBytes(StandardCharsets.UTF_8),null).category()).isEqualTo(CONTRACT_ERROR);
        String transition = revoked.replace("ENTITLEMENT_REVOKED","TRANSITION_CONTEXT_CONFLICT");
        assertThat(decoder.decode(RESERVE,409,transition.getBytes(StandardCharsets.UTF_8),null).category()).isEqualTo(TRANSITION_CONTEXT_CONFLICT);
        assertThat(decoder.decode(STATUS,409,transition.getBytes(StandardCharsets.UTF_8),null).category()).isEqualTo(CONTRACT_ERROR);
    }
}
