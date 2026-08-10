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
                    configurationError("카탈로그에 mockExamId가 중복되었습니다");
                }
                if (Boolean.FALSE.equals(mockExam.getActive()) || !hasQuestions(mockExam)) {
                    continue;
                }

                int sequence = effectiveSequence(mockExam);
                if (!sequences.add(sequence)) {
                    configurationError("활성 sequence가 중복되었습니다: sequence=" + sequence);
                }
                assignable.add(new CatalogExam(mockExam, sequence));
            }
        }

        if (assignable.isEmpty()) {
            configurationError("배정 가능한 활성 문항 포함 MockExam이 없습니다");
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
            configurationError("mockExamId 조회 결과가 중복되었습니다");
        }
        MockExam mockExam = matches.getFirst();
        validateMockExamId(mockExam);
        if (!hasQuestions(mockExam)) {
            configurationError("MockExam에 배정 가능한 문항이 없습니다");
        }
        return mockExam;
    }

    private String validateMockExamId(MockExam mockExam) {
        if (mockExam == null || mockExam.getMockExamId() == null || mockExam.getMockExamId().isBlank()) {
            configurationError("MockExam의 mockExamId는 비어 있을 수 없습니다");
        }
        String mockExamId = mockExam.getMockExamId();
        if (!mockExamId.equals(mockExamId.trim())) {
            configurationError("mockExamId 앞뒤에 공백을 포함할 수 없습니다");
        }
        return mockExamId;
    }

    private int effectiveSequence(MockExam mockExam) {
        Integer configured = mockExam.getSequence();
        if (configured != null) {
            if (configured < 1 || configured > Integer.MAX_VALUE) {
                configurationError("sequence는 1 이상 Integer.MAX_VALUE 이하여야 합니다: "
                        + mockExam.getMockExamId());
            }
            return configured;
        }

        String mockExamId = mockExam.getMockExamId();
        Matcher matcher = mockExamId == null
                ? LEGACY_SEQUENCE_PATTERN.matcher("")
                : LEGACY_SEQUENCE_PATTERN.matcher(mockExamId);
        if (!matcher.find()) {
            configurationError("레거시 sequence를 추출할 수 없습니다: " + mockExamId);
        }

        try {
            int derived = Integer.parseInt(matcher.group(1));
            if (derived < 1) {
                configurationError("추출한 sequence는 1 이상이어야 합니다: " + mockExamId);
            }
            return derived;
        } catch (NumberFormatException invalidSequence) {
            configurationError("레거시 sequence가 지원하는 정수 범위를 벗어났습니다: " + mockExamId);
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
            configurationError("MockExam sequence 타입 또는 범위가 Java Integer와 호환되지 않습니다");
            return List.of();
        }
    }

    private List<MockExam> loadByMockExamId(String mockExamId) {
        try {
            return mockExamRepository.findAllByMockExamId(mockExamId);
        } catch (ConversionException | MappingException invalidMappedSequence) {
            configurationError("MockExam sequence 타입 또는 범위가 Java Integer와 호환되지 않습니다");
            return List.of();
        }
    }

    private void configurationError(String detail) {
        log.error("MockExam 카탈로그 설정 오류: {}", detail);
        throw new ExamsException(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR);
    }

    public record CatalogExam(MockExam mockExam, int sequence) {
    }
}
