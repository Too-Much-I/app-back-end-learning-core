package web.tosunsaeng.domain.exams.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mapping.MappingException;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockExamCatalogServiceTest {

    @Mock
    private MockExamRepository mockExamRepository;

    private MockExamCatalogService service;

    @BeforeEach
    void setUp() {
        service = new MockExamCatalogService(mockExamRepository);
    }

    @Test
    void inactiveExamIsExcluded() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_001", 1, false, true),
                mockExam("mock_exam_002", 2, true, true)
        ));

        List<MockExamCatalogService.CatalogExam> result = service.findAssignableExams();

        assertEquals(List.of("mock_exam_002"), result.stream()
                .map(candidate -> candidate.mockExam().getMockExamId())
                .toList());
    }

    @Test
    void duplicateEffectiveSequenceFailsSafely() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_001", 1, true, true),
                mockExam("mock_exam_099", 1, true, true)
        ));

        ExamsException exception = assertThrows(ExamsException.class, service::findAssignableExams);

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
    }

    @Test
    void duplicateIdWithDifferentSequencesFailsSafely() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_001", 1, true, true),
                mockExam("mock_exam_001", 2, true, true)
        ));

        ExamsException exception = assertThrows(ExamsException.class, service::findAssignableExams);

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
    }

    @Test
    void duplicateIdWithSameSequenceFailsSafely() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_001", 1, true, true),
                mockExam("mock_exam_001", 1, true, true)
        ));

        ExamsException exception = assertThrows(ExamsException.class, service::findAssignableExams);

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
    }

    @Test
    void duplicateInactiveIdAlsoFailsSafely() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_001", 1, false, true),
                mockExam("mock_exam_001", 2, false, true)
        ));

        assertThrows(ExamsException.class, service::findAssignableExams);
    }

    @Test
    void nullBlankAndWhitespaceIdsFailSafely() {
        for (String invalidId : new String[]{null, "", "   ", " mock_exam_001", "mock_exam_001 "}) {
            when(mockExamRepository.findAll()).thenReturn(List.of(
                    mockExam(invalidId, 1, true, true)
            ));

            assertThrows(ExamsException.class, service::findAssignableExams);
        }
    }

    @Test
    void uniqueIdsAreReturnedInSequenceOrder() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_002", 2, true, true),
                mockExam("mock_exam_001", 1, true, true)
        ));

        List<MockExamCatalogService.CatalogExam> result = service.findAssignableExams();

        assertEquals(List.of("mock_exam_001", "mock_exam_002"), result.stream()
                .map(candidate -> candidate.mockExam().getMockExamId())
                .toList());
    }

    @Test
    void requiredExamLookupRejectsAmbiguousRepositoryResult() {
        when(mockExamRepository.findAllByMockExamId("mock_exam_001")).thenReturn(List.of(
                mockExam("mock_exam_001", 1, true, true),
                mockExam("mock_exam_001", 2, true, true)
        ));

        ExamsException exception = assertThrows(
                ExamsException.class,
                () -> service.getRequiredExam("mock_exam_001")
        );

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
    }

    @Test
    void missingSequenceUsesTrailingLegacyIdNumberAndSortsNumerically() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_010", null, null, true),
                mockExam("mock_exam_002", null, null, true)
        ));

        List<MockExamCatalogService.CatalogExam> result = service.findAssignableExams();

        assertEquals(List.of(2, 10), result.stream()
                .map(MockExamCatalogService.CatalogExam::sequence)
                .toList());
        assertEquals(List.of("mock_exam_002", "mock_exam_010"), result.stream()
                .map(candidate -> candidate.mockExam().getMockExamId())
                .toList());
    }

    @Test
    void unparseableMissingSequenceFailsSafely() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_final", null, true, true)
        ));

        ExamsException exception = assertThrows(ExamsException.class, service::findAssignableExams);

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
    }

    @Test
    void emptyExamIsExcludedInsteadOfBeingAssigned() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_001", 1, true, false),
                mockExam("mock_exam_002", 2, true, true)
        ));

        List<MockExamCatalogService.CatalogExam> result = service.findAssignableExams();

        assertEquals(List.of("mock_exam_002"), result.stream()
                .map(candidate -> candidate.mockExam().getMockExamId())
                .toList());
    }

    @Test
    void nonPositiveSequenceFailsSafely() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_001", 0, true, true)
        ));

        ExamsException exception = assertThrows(ExamsException.class, service::findAssignableExams);

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
    }

    @Test
    void legacySuffixAtJavaIntegerMaximumIsAllowed() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_2147483647", null, true, true)
        ));

        List<MockExamCatalogService.CatalogExam> result = service.findAssignableExams();

        assertEquals(Integer.MAX_VALUE, result.getFirst().sequence());
    }

    @Test
    void legacySuffixAboveJavaIntegerMaximumFailsSafely() {
        when(mockExamRepository.findAll()).thenReturn(List.of(
                mockExam("mock_exam_2147483648", null, true, true)
        ));

        ExamsException exception = assertThrows(ExamsException.class, service::findAssignableExams);

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
    }

    @Test
    void repositorySequenceMappingOverflowIsReportedAsCatalogConfigurationError() {
        when(mockExamRepository.findAll())
                .thenThrow(new MappingException("internal-bson-document-must-not-be-exposed"));

        ExamsException exception = assertThrows(ExamsException.class, service::findAssignableExams);

        assertSame(ErrorStatus._EXAM_CATALOG_CONFIGURATION_ERROR, exception.getCode());
        assertEquals(false, String.valueOf(exception.getMessage())
                .contains("internal-bson-document-must-not-be-exposed"));
    }

    private static MockExam mockExam(
            String mockExamId,
            Integer sequence,
            Boolean active,
            boolean withQuestion) {
        return MockExam.builder()
                .mockExamId(mockExamId)
                .sequence(sequence)
                .active(active)
                .questions(withQuestion
                        ? List.of(Question.builder().questionNumber(1).build())
                        : List.of())
                .build();
    }
}
