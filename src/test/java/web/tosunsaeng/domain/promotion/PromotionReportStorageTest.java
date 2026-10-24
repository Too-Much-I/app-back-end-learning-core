package web.tosunsaeng.domain.promotion;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PromotionReportStorageTest {
    @Test void uploadUsesPrivateBucketEncryptionAndNoPublicAcl() {
        var s3=mock(S3Client.class);
        new PromotionReportStorage(s3,"private-fixture").put("promotion-reports/opaque",new byte[]{1},"application/pdf");
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(),any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("private-fixture");
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(request.getValue().acl()).isNull();
    }
}
