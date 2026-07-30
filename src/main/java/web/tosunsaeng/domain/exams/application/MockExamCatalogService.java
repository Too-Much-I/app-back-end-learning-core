package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionException;
import org.springframework.data.mapping.MappingException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockExamCatalogService {

    private static final Pattern LEGACY_SEQUENCE_PATTERN = Pattern.compile("(\\d+)$");

    private final MockExamRepository mockExamRepository;

    public List<CatalogExam> findAssignableExams() {
        List<MockExam> documents = loadCatalog();
        List<CatalogExam> assignable = new ArrayList<>();
        Set<Integer> sequences = new HashSet<>();
        Set<String> mockExamIds = new HashSet<>();

        if (documents != null) {
            for (MockExam mockExam : documents) {
                String mockExamId = validateMockExamId(mockExam);
                if (!mockExamIds.add(mockExamId)) {
                    configurationError("duplicate mockExamId in catalog");
                }
                if (Boolean.FALSE.equals(mockExam.getActive()) || !hasQuestions(mockExam)) {
                    continue;
                }

                int sequence = effectiveSequence(mockExam);
                if (!sequences.add(sequence)) {
                    configurationError("duplicate active sequence=" + sequence);
                }
                assignable.add(new CatalogExam(mockExam, sequence));
            }
        }

        if (assignable.isEmpty()) {
            configurationError("no active non-empty MockExam is assignable");
        }

        assignable.sort(Comparator.comparingInt(CatalogExam::sequence));
        return List.copyOf(assignable);
    }

    public MockExam getRequiredExam(String mockExamId) {
        List<MockExam> matches = loadByMockExamId(mockExamId);
        if (matches == null || matches.isEmpty()) {
            throw new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND);
        }
        if (matches.size() > 1) {
            configurationError("duplicate mockExamId lookup result");
        }
        MockExam mockExam = matches.getFirst();
        validateMockExamId(mockExam);
        if (!hasQuestions(mockExam)) {
            configurationError("MockExam has no assignable questions");
        }
        return mockExam;
    }

    private String validateMockExamId(MockExam mockExam) {
        if (mockExam == null || mockExam.getMockExamId() == null || mockExam.getMockExamId().isBlank()) {
            configurationError("MockExam requires a non-blank mockExamId");
        }
        String mockExamId = mockExam.getMockExamId();
        if (!mockExamId.equals(mockExamId.trim())) {
            configurationError("mockExamId must not contain leading or trailing whitespace");
        }
        return mockExamId;
    }

    private int effectiveSequence(MockExam mockExam) {
        Integer configured = mockExam.getSequence();
        if (configured != null) {
            if (configured < 1 || configured > Integer.MAX_VALUE) {
                configurationError("sequence must be within 1..Integer.MAX_VALUE: "
                        + mockExam.getMockExamId());
            }
            return configured;
        }

        String mockExamId = mockExam.getMockExamId();
        Matcher matcher = mockExamId == null
                ? LEGACY_SEQUENCE_PATTERN.matcher("")
                : LEGACY_SEQUENCE_PATTERN.matcher(mockExamId);
        if (!matcher.find()) {
            configurationError("legacy sequence cannot be derived: " + mockExamId);
        }

        try {
            int derived = Integer.parseInt(matcher.group(1));
            if (derived < 1) {
                configurationError("derived sequence must be at least 1: " + mockExamId);
            }
            return derived;
        } catch (NumberFormatException invalidSequence) {
            configurationError("legacy sequence is outside the supported integer range: " + mockExamId);
            return -1;
        }
    }

    private static boolean hasQuestions(MockExam mockExam) {
        return mockExam != null
                && mockExam.getQuestions() != null
                && mockExam.getQuestions().stream()
                .anyMatch(question -> question != null
                        && question.getQuestionNumber() != null
                        && question.getQuestionNumber() > 0);
    }

    private List<MockExam> loadCatalog() {
        try {
            return mockExamRepository.findAll();
        } catch (ConversionException | MappingException invalidMappedSequence) {
            configurationError("MockExam sequence type or range is incompatible with Java Integer");
            return List.of();
        }
    }

    private List<MockExam> loadByMockExamId(String mockExamId) {
        try {
            return mockExamRepository.findAllByMockExamId(mockExamId);
        } catch (ConversionException | MappingException invalidMappedSequence) {
            configurationError("MockExam sequence type or range is incompatible with Java Integer");
            return List.of();
        }
    }

    private void configurationError(String detail) {
        log.error("MockExam catalog configuration error: {}", detail);
        throw new ExamsException(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR);
    }

    public record CatalogExam(MockExam mockExam, int sequence) {
    }
}
