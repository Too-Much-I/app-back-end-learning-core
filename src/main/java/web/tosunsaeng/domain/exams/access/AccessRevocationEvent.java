package web.tosunsaeng.domain.exams.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.*;
import java.util.*;
import static web.tosunsaeng.domain.exams.access.AccessEventJson.*;

public record AccessRevocationEvent(String eventId,String consumerScopeId,String occurredAt,String userId,
        String attemptGroupId,String reason,String confirmedSessionId,String digest) {
    public static final int MAX_BYTES=16384;
    public static final String UUID4="[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final List<String> FIELDS=List.of("eventId","eventType","schemaVersion","producer","consumerScopeId",
            "occurredAt","userId","attemptGroupId","reason","confirmedSessionId");

    public static AccessRevocationEvent decode(byte[] raw,String scope) {
        if(raw.length>MAX_BYTES) throw new AccessEventFailure(413,"INVALID_REQUEST");
        try {
            var n=parse(raw); fields(n,new HashSet<>(FIELDS));
            for(String k:List.of("eventId","attemptGroupId")) require(text(n,k).matches(UUID4));
            require(text(n,"userId").matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"));
            require(n.get("schemaVersion").isIntegralNumber());
            for(String k:List.of("eventType","producer","consumerScopeId","reason")) text(n,k);
            String at=text(n,"occurredAt");
            require(at.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z"));
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withResolverStyle(ResolverStyle.STRICT).parse(at);
            var session=n.get("confirmedSessionId");
            require(session.isNull() || session.isTextual() && !session.textValue().isEmpty()
                    && session.textValue().codePointCount(0,session.textValue().length())<=128);
            if(!n.get("schemaVersion").bigIntegerValue().equals(java.math.BigInteger.ONE)
                    || !text(n,"eventType").equals("AttemptGroupAccessRevoked") || !text(n,"producer").equals("billing")
                    || !text(n,"consumerScopeId").equals(scope) || !Set.of("PURCHASE_REFUNDED","PURCHASE_REVOKED").contains(text(n,"reason")))
                throw new AccessEventFailure(422,"UNSUPPORTED_CONTRACT");
            StringJoiner canonical=new StringJoiner(",","{","}");
            for(String k:FIELDS) {
                var v=n.get(k); canonical.add(quote(k)+":"+(v.isNull()?"null":v.isIntegralNumber()?v.bigIntegerValue().toString():quote(v.textValue())));
            }
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
            return new AccessRevocationEvent(text(n,"eventId"),scope,at,text(n,"userId"),text(n,"attemptGroupId"),text(n,"reason"),
                    session.isNull()?null:session.textValue(),digest);
        } catch(AccessEventFailure e) { throw e; }
        catch(Exception invalid) { throw new AccessEventFailure(400,"INVALID_REQUEST"); }
    }
}
