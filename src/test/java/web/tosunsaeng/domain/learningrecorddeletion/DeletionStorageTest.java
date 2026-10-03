package web.tosunsaeng.domain.learningrecorddeletion;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import web.tosunsaeng.domain.learningrecorddeletion.domain.DeletionTarget;
import web.tosunsaeng.domain.learningrecorddeletion.infrastructure.S3RedisDeletionStorage;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeletionStorageTest {
    final S3Client s3 = mock(S3Client.class);
    final RedisTemplate<String, String> redis = mock(RedisTemplate.class);
    final S3RedisDeletionStorage storage = new S3RedisDeletionStorage(s3, redis, "fixture-audio");
    final DeletionTarget target = DeletionTarget.draining("139f345b-9be8-43a3-a63d-9108df972741",
            "00000000-0000-0000-0000-000000000001", DeletionTarget.Type.EXAM, "exam-old");

    @Test void deletesOneExactPrefixPageAndRequiresFreshEmptyPass() {
        when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenReturn(GetBucketVersioningResponse.builder().build());
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("temp/exam-old/q_1_r0.wav").build()).isTruncated(true).build(),
                ListObjectsV2Response.builder().build());
        when(s3.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().build());
        assertThat(storage.sweep(target)).isFalse(); assertThat(storage.sweep(target)).isTrue();
        var requests = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3, times(2)).listObjectsV2(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(r -> {
            assertThat(r.prefix()).isEqualTo("temp/exam-old/"); assertThat(r.continuationToken()).isNull(); assertThat(r.maxKeys()).isEqualTo(1000);
        });
    }
    @Test void partialDeleteErrorDoesNotPretendSuccess() {
        when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenReturn(GetBucketVersioningResponse.builder().build());
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("temp/exam-old/q_1_r0.wav").build()).build());
        when(s3.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().code("AccessDenied").build()).build());
        assertThatThrownBy(() -> storage.sweep(target)).isInstanceOf(IllegalStateException.class);
    }
    @Test void versionedBucketDeletesBothVersionAndDeleteMarker() {
        when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenReturn(GetBucketVersioningResponse.builder().status(BucketVersioningStatus.SUSPENDED).build());
        when(s3.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(ListObjectVersionsResponse.builder()
                .versions(ObjectVersion.builder().key("temp/exam-old/q_1_r0.wav").versionId("fixture-version").build())
                .deleteMarkers(DeleteMarkerEntry.builder().key("temp/exam-old/q_1_r0.wav").versionId("fixture-marker").build()).build());
        when(s3.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().build());
        assertThat(storage.sweep(target)).isFalse();
        var request = ArgumentCaptor.forClass(DeleteObjectsRequest.class); verify(s3).deleteObjects(request.capture());
        assertThat(request.getValue().delete().objects()).extracting(ObjectIdentifier::versionId).containsExactly("fixture-version", "fixture-marker");
    }
    @Test void outOfScopeListingCannotDeleteOtherUsersObjects() {
        when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class))).thenReturn(GetBucketVersioningResponse.builder().build());
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("temp/other/q_1_r0.wav").build()).build());
        assertThatThrownBy(() -> storage.sweep(target)).isInstanceOf(IllegalStateException.class);
        verify(s3, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
    @Test void redisUnknownIsNotEmptyAndOnlyExactKeyIsTouched() {
        when(redis.hasKey("exam:status:exam-old")).thenReturn(null, false);
        assertThat(storage.clearCache(target)).isFalse(); assertThat(storage.clearCache(target)).isTrue();
        verify(redis, times(2)).delete("exam:status:exam-old");
        verifyNoMoreInteractions(s3);
    }
}
