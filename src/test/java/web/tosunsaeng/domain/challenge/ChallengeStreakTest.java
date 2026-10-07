package web.tosunsaeng.domain.challenge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChallengeStreakTest {
    final ChallengeStore store = mock(ChallengeStore.class);
    final ChallengeCatalog catalog = mock(ChallengeCatalog.class);
    final ChallengeService service = new ChallengeService(store, null, catalog, null, Clock.systemUTC());

    void fixture(String today, String base, Set<String> dates) {
        when(catalog.today()).thenReturn(LocalDate.parse(today));
        when(catalog.baseDate()).thenReturn(LocalDate.parse(base));
        when(store.submittedDates(eq("owner"), anyString(), anyString())).thenAnswer(call -> {
            String from = call.getArgument(1), to = call.getArgument(2);
            return dates.stream().filter(d -> d.compareTo(from) >= 0 && d.compareTo(to) <= 0).collect(Collectors.toSet());
        });
    }

    @ParameterizedTest
    @CsvSource({"'',0", "2026-10-05,1", "2026-10-04,1", "2026-10-03,0",
            "2026-10-05;2026-10-04;2026-10-03,3", "2026-10-04;2026-10-03,2",
            "2026-10-05;2026-10-03,1"})
    void todayGraceAndGaps(String dates, int expected) {
        fixture("2026-10-05", "2026-01-01", Set.of(dates.split(";")));
        assertThat(service.history("owner", null).currentStreakDays()).isEqualTo(expected);
        verify(catalog, times(1)).today();
    }

    @Test void crossesChunksMonthsAndYearsAndIgnoresRequestedMonth() {
        LocalDate start = LocalDate.parse("2025-11-25"), end = LocalDate.parse("2026-01-05");
        Set<String> dates = start.datesUntil(end.plusDays(1)).map(LocalDate::toString).collect(Collectors.toSet());
        fixture(end.toString(), start.toString(), dates);
        var emptyMonth = service.history("owner", "2025-10");
        assertThat(emptyMonth.dates()).isEmpty();
        assertThat(emptyMonth.currentStreakDays()).isEqualTo(dates.size());
        assertThat(service.history("owner", "2026-01").currentStreakDays()).isEqualTo(dates.size());
        verify(store, times(2)).submittedDates("owner", "2025-12-05", "2026-01-05");
        verify(store, times(2)).submittedDates("owner", "2025-11-25", "2025-12-04");
    }

    @Test void leapDayAndKstMidnightUseOneTodaySnapshot() {
        fixture("2024-03-01", "2024-02-28", Set.of("2024-02-28", "2024-02-29"));
        when(catalog.today()).thenReturn(LocalDate.parse("2024-03-01"), LocalDate.parse("2024-03-02"));
        assertThat(service.history("owner", null).currentStreakDays()).isEqualTo(2);
        verify(catalog, times(1)).today();
    }

    @Test void futureMonthStillRejectedWithoutStreakQuery() {
        fixture("2026-10-05", "2026-10-01", Set.of());
        assertThatThrownBy(() -> service.history("owner", "2026-11")).isInstanceOf(ChallengeFailure.class);
        verifyNoInteractions(store);
    }

    @Test void historySerializationKeepsExistingFieldsAndAddsInteger() throws Exception {
        fixture("2026-10-05", "2026-10-01", Set.of("2026-10-04"));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(service.history("owner", null));
        assertThat(json.get("yearMonth").asText()).isEqualTo("2026-10");
        assertThat(json.get("dates").isArray()).isTrue();
        assertThat(json.get("currentStreakDays").isIntegralNumber()).isTrue();
        assertThat(json.get("currentStreakDays").asInt()).isEqualTo(1);
    }
}
