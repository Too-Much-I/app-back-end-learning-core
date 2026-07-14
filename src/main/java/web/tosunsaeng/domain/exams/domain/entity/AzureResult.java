package web.tosunsaeng.domain.exams.domain.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document(collection = "azure_results")
public class AzureResult {

    @Id
    private String id;

    @Field("exam_id")
    private String examId;

    @Field("question_number")
    private Integer questionNumber;

    @Field("retry_count")
    private Integer retryCount;

    @Field("raw_data")
    private Map<String, Object> rawData;
}