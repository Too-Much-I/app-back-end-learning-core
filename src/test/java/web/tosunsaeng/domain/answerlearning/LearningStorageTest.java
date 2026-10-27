package web.tosunsaeng.domain.answerlearning;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.io.ByteArrayInputStream;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static web.tosunsaeng.domain.answerlearning.LearningFixtures.*;

class LearningStorageTest {
    @Test void reusedMediaCanOnlyUseTheValidatedSourcePrefixAndBucket() {
        var source=new LearningMediaSource("old-dataset","old-v1","test-learning-bucket","answer-learning/old-dataset/old-v1");
        var row=new Document("_id","audio").append("bucket","test-learning-bucket")
                .append("key","answer-learning/old-dataset/old-v1/media/audio.mp3");
        assertThat(storage.media(row,source).getString("url")).contains("old-dataset/old-v1/media/audio.mp3");
        assertThatThrownBy(() -> storage.media(row)).hasMessage("LEARNING_UNAVAILABLE");
        row.put("key","answer-learning/old-dataset/other/media/audio.mp3");
        assertThatThrownBy(() -> storage.media(row,source)).hasMessage("LEARNING_UNAVAILABLE");
        assertThatThrownBy(() -> LearningMediaSource.parse(new Document("datasetId","../other")
                .append("contentVersion","v1").append("bucket","test-learning-bucket"),config()))
                .hasMessage("LEARNING_UNAVAILABLE");
    }
    S3Client s3=mock(S3Client.class);
    S3Presigner signer=S3Presigner.builder().region(Region.AP_NORTHEAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access","test-secret"))).build();
    LearningStorage storage=new LearningStorage(s3,signer,config(),CLOCK);
    @Test void putUrlsCannotMutateTheWorkerKeyAndOnlyRequireNormalUploadHeaders() {
        var s=submission(); s.uploadSize=123;
        var url=storage.upload(s);
        assertThat(url.url()).contains("answer-learning-uploads/"+ID).doesNotContain("answer-learning-submissions");
        assertThat(url.expiresAt()).isEqualTo(NOW.plusSeconds(300)); assertThat(url.maxBytes()).isEqualTo(8388608);
        verifyNoInteractions(s3);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={403,404})
    void frozenAudioIsWriteOnceWithChecksumAndSurvivesStagingOverwrite(int absentStatus) {
        var s=submission(); s.externalWriteUntil=NOW.plusSeconds(60); s.uploadSize=4; byte[] bytes={1,2,3,4};
        when(s3.headObject(any(HeadObjectRequest.class))).thenAnswer(i -> {
            HeadObjectRequest r=i.getArgument(0);
            if (r.key().equals(LearningStorage.frozenKey(ID))) throw S3Exception.builder().statusCode(absentStatus).build();
            return HeadObjectResponse.builder().contentLength(4L).contentType("audio/mp4").eTag("\"staging-etag\"").build();
        });
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().contentType("audio/mp4").build(),
                AbortableInputStream.create(new ByteArrayInputStream(bytes))));
        when(s3.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenReturn(PutObjectResponse.builder().eTag("\"fixed-etag\"").build());
        var audio=storage.freeze(s); assertThat(audio.sha256()).isEqualTo(LearningStorage.sha(bytes));
        var c=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class); verify(s3).putObject(c.capture(),any(RequestBody.class));
        assertThat(c.getValue().key()).isEqualTo(LearningStorage.frozenKey(ID)); assertThat(c.getValue().ifNoneMatch()).isEqualTo("*");
        var get=org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class); verify(s3).getObject(get.capture()); assertThat(get.getValue().ifMatch()).isEqualTo("\"staging-etag\"");
    }
    @Test void existingFrozenRecordingNeverRereadsStaging() {
        var s=submission();
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(10L).contentType("audio/mp4")
                .eTag("\"fixed\"").metadata(Map.of("sha256","a".repeat(64))).build());
        assertThat(storage.freeze(s).sha256()).isEqualTo("a".repeat(64)); verify(s3,never()).getObject(any(GetObjectRequest.class));
        verify(s3,never()).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
    @Test void rejectsWrongMimeOversizeAndOtherBucketMedia() {
        assertThatThrownBy(()->LearningStorage.check("audio/wav",100L,100)).hasMessage("INVALID_AUDIO");
        assertThatThrownBy(()->LearningStorage.check("audio/mp4",8388609L,8388609)).hasMessage("INVALID_AUDIO");
        assertThatThrownBy(()->LearningStorage.check("audio/mp4",10L,11)).hasMessage("INVALID_AUDIO");
        assertThatThrownBy(()->storage.media(new Document("bucket","other").append("key","private"))).hasMessage("LEARNING_UNAVAILABLE");
    }
    @Test void contentUrlsHaveExpiryButDoNotExposeStorageFields() {
        Document media=storage.media(new Document("_id","photo").append("bucket","test-learning-bucket")
                .append("key","answer-learning/test-dataset/test-v1/media/photo.jpg").append("width",1200).append("height",800));
        assertThat(media).containsOnlyKeys("id","url","expiresAt","width","height");
        assertThat(media.getString("url")).startsWith("https://");
    }
}
