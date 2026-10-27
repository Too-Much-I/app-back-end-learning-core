package web.tosunsaeng.domain.promotion;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Dedicated private bucket. No public ACL, filename, owner ID or presigned read URL. */
public class PromotionReportStorage {
    private final S3Client s3;
    private final String bucket;
    public PromotionReportStorage(S3Client s3, String bucket) { this.s3=s3; this.bucket=bucket; }
    public void put(String key, byte[] bytes, String type) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(type)
                .serverSideEncryption(ServerSideEncryption.AES256).build(), RequestBody.fromBytes(bytes));
    }
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
