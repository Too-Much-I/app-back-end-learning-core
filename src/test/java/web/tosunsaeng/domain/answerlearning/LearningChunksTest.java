package web.tosunsaeng.domain.answerlearning;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class LearningChunksTest {
    Document chunk(int from,int to) { return new Document("from",from).append("to",to); }
    @Test void optionalAndInclusiveRangesPreserveLongPhrases() {
        LearningCatalog.validateChunks(new Document(),10);
        LearningCatalog.validateChunks(new Document("chunks",null),10);
        LearningCatalog.validateChunks(new Document("chunks",List.of(chunk(0,9))),10);
        LearningCatalog.validateChunks(new Document("chunks",List.of(chunk(0,2),chunk(3,9))),10);
    }
    @Test void gapsOverlapsAndOutOfBoundsAreRejected() {
        for (var chunks:List.of(List.of(chunk(1,9)),List.of(chunk(0,2),chunk(2,9)),
                List.of(chunk(0,2),chunk(4,9)),List.of(chunk(0,10)),List.of(chunk(0,8)))) {
            assertThatThrownBy(() -> LearningCatalog.validateChunks(new Document("chunks",chunks),10))
                    .hasMessage("LEARNING_UNAVAILABLE");
        }
    }
}
