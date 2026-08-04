package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ModelAnswerCatalogService {

    private static final String RESOURCE_PATTERN =
            "classpath*:model-answers/*_spoken_word_sequences.json";

    private final Map<String, Map<Integer, List<SpokenWord>>> catalog;

    public ModelAnswerCatalogService(ObjectMapper objectMapper) {
        this.catalog = loadCatalog(objectMapper);
    }

    public Optional<List<SpokenWord>> findSpokenWordSequence(
            String mockExamId,
            Integer questionNumber) {
        if (mockExamId == null || mockExamId.isBlank() || questionNumber == null) {
            return Optional.empty();
        }

        Map<Integer, List<SpokenWord>> questions = catalog.get(mockExamId);
        if (questions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(questions.get(questionNumber));
    }

    private static Map<String, Map<Integer, List<SpokenWord>>> loadCatalog(ObjectMapper objectMapper) {
        Map<String, Map<Integer, List<SpokenWord>>> loaded = new LinkedHashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            Resource[] resources = resolver.getResources(RESOURCE_PATTERN);
            if (resources.length == 0) {
                throw new IllegalStateException("Model answer spoken-word metadata is missing");
            }

            for (Resource resource : resources) {
                CatalogDocument document;
                try (InputStream inputStream = resource.getInputStream()) {
                    document = objectMapper.readValue(inputStream, CatalogDocument.class);
                }
                registerDocument(loaded, document);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Model answer spoken-word metadata cannot be loaded", exception);
        }

        return Map.copyOf(loaded);
    }

    private static void registerDocument(
            Map<String, Map<Integer, List<SpokenWord>>> loaded,
            CatalogDocument document) {
        if (document == null || document.mockExamId() == null || document.mockExamId().isBlank()) {
            throw new IllegalStateException("Model answer metadata requires mockExamId");
        }
        if (loaded.containsKey(document.mockExamId())) {
            throw new IllegalStateException("Duplicate model answer metadata: " + document.mockExamId());
        }

        Map<Integer, List<SpokenWord>> questions = new LinkedHashMap<>();
        registerQuestion(questions, 1, document.q1());
        registerQuestion(questions, 2, document.q2());
        loaded.put(document.mockExamId(), Map.copyOf(questions));
    }

    private static void registerQuestion(
            Map<Integer, List<SpokenWord>> questions,
            int questionNumber,
            QuestionSequence questionSequence) {
        if (questionSequence == null
                || questionSequence.spokenWordSequence() == null
                || questionSequence.spokenWordSequence().isEmpty()) {
            throw new IllegalStateException(
                    "Model answer spoken-word metadata is missing for question " + questionNumber);
        }
        questions.put(questionNumber, List.copyOf(questionSequence.spokenWordSequence()));
    }

    private record CatalogDocument(
            String mockExamId,
            QuestionSequence q1,
            QuestionSequence q2) {
    }

    private record QuestionSequence(List<SpokenWord> spokenWordSequence) {
    }

    public record SpokenWord(
            Integer index,
            Integer segmentIndex,
            Integer wordIndex,
            String word,
            Long offset,
            Long duration,
            Double accuracyScore,
            Double pronunciationScore,
            String errorType) {
    }
}
