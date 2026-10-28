package web.tosunsaeng.domain.notification;

import java.time.*;

public final class PromotionPushPolicy {
    public static final String TYPE="CASHBACK_EVENT";
    public static final String ROUTE="CASHBACK_EVENT_HOME";
    public static final String OPT_OUT_NOTICE="수신거부: 설정 > 알림 > 이벤트·혜택 알림";
    public static final String TITLE="(광고) 토선생 성적 인증 이벤트";
    public static final String BODY="성적을 입력하고 기프티콘을 받아보세요!";
    private PromotionPushPolicy() {}
    public static boolean inWindow(Instant now,Instant opens,Instant closes){
        if(opens==null || closes==null || now.isBefore(opens) || !now.isBefore(closes))return false;
        var local=now.atZone(ReminderPolicy.KST); var day=local.getDayOfWeek(); var time=local.toLocalTime();
        return (day==DayOfWeek.WEDNESDAY || day==DayOfWeek.THURSDAY)
                && !time.isBefore(LocalTime.NOON) && time.isBefore(LocalTime.of(12,15));
    }
    public static Instant expires(Instant now,Instant closes){
        Instant end=now.atZone(ReminderPolicy.KST).toLocalDate().atTime(12,15).atZone(ReminderPolicy.KST).toInstant();
        return end.isBefore(closes)?end:closes;
    }
    public static String marker(String campaign,String owner,Instant now){
        return "promotion:"+NotificationStore.hash(campaign+":"+owner+":"+ReminderPolicy.day(now));
    }
}
