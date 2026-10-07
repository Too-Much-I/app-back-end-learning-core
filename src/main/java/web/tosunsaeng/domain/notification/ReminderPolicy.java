package web.tosunsaeng.domain.notification;

import java.time.*;

public final class ReminderPolicy {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final String TYPE = "DAILY_EXAM_REMINDER";
    private ReminderPolicy() {}
    public static LocalDate day(Instant now) { return now.atZone(KST).toLocalDate(); }
    public static Instant start(LocalDate day) { return day.atStartOfDay(KST).toInstant(); }
    public static Instant end(LocalDate day) { return day.atTime(21, 15).atZone(KST).toInstant(); }
    public static boolean inWindow(Instant now) {
        LocalTime time = now.atZone(KST).toLocalTime();
        return !time.isBefore(LocalTime.of(21, 0)) && time.isBefore(LocalTime.of(21, 15));
    }
    public static Instant expiry(LocalDate day) { return start(day.plusDays(31)); }
}
