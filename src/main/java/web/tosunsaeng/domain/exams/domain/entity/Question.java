package web.tosunsaeng.domain.exams.domain.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "questions")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Question {
    @Id
    private String id;
    private String examPaperId; // "mock_exam_001"
    private Integer partNumber;
    private Integer questionNumber;
    private String audioUrl;

    private Integer prepTimeSec;
    private Integer speakTimeSec;

    // 유형별 데이터 (하나만 존재하거나 조합될 수 있음)
    private String referenceText;
    private String imageUrl;
    private String question;
    private TableContext tableContext;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TableContext {
        private String title;
        private String location;
        private String date;
        private String fee;
        private List<TableItem> items;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TableItem {
        private String time;
        private String sessionTitle;
        private String speaker;
        private String note;
    }
}