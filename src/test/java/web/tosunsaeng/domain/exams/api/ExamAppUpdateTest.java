package web.tosunsaeng.domain.exams.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.application.ExamReadService;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExamAppUpdateTest {
    @ParameterizedTest
    @ValueSource(strings = {"unset", "false", "true"})
    void summaryAddsServerPolicyWithoutChangingStoredResult(String setting) {
        ExamService service = mock(ExamService.class);
        var summary = ExamResponseDTO.SummaryResult.builder().examId("exam-fixture")
                .totalScore(150).summary("feedback").totalSolvedQuestions(11).build();
        when(service.getExamSummary("exam-fixture")).thenReturn(summary);
        var runner = new ApplicationContextRunner()
                .withBean(ExamService.class, () -> service)
                .withBean(ExamReadService.class, () -> mock(ExamReadService.class))
                .withUserConfiguration(ExamRestController.class);
        if (!setting.equals("unset")) runner = runner.withPropertyValues("APP_UPDATE_REQUIRED=" + setting);
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvcBuilders.standaloneSetup(context.getBean(ExamRestController.class)).build()
                    .perform(get("/api/v1/exams/exam-fixture/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.result.examId").value("exam-fixture"))
                    .andExpect(jsonPath("$.result.totalScore").value(150))
                    .andExpect(jsonPath("$.result.summary").value("feedback"))
                    .andExpect(jsonPath("$.result.totalSolvedQuestions").value(11))
                    .andExpect(jsonPath("$.result.appUpdateRequired").value(setting.equals("true")));
            assertThat(summary.isAppUpdateRequired()).isFalse();
            verify(service).getExamSummary("exam-fixture");
        });
    }
}
