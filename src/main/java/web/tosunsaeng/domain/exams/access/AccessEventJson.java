package web.tosunsaeng.domain.exams.access;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Strict bounded scalar JSON shared only by this fixed Billing trust profile. */
public final class AccessEventJson {
    private static final ObjectMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private AccessEventJson() {}
    public static JsonNode parse(byte[] bytes) throws Exception {
        String s=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        require(!s.startsWith("\ufeff"));
        JsonNode n=JSON.readTree(s); require(n!=null); scalar(n); return n;
    }
    private static void scalar(JsonNode n) {
        if(n.isTextual()) scalars(n.textValue());
        if(n.isObject()) n.fieldNames().forEachRemaining(AccessEventJson::scalars);
        if(n.isContainerNode()) n.forEach(AccessEventJson::scalar);
    }
    private static void scalars(String s) { s.codePoints().forEach(cp->require(cp<0xd800 || cp>0xdfff)); }
    public static String text(JsonNode n,String key) {
        JsonNode v=n.get(key); require(v!=null && v.isTextual() && !v.textValue().isEmpty()); return v.textValue();
    }
    public static void fields(JsonNode n,Set<String> expected) {
        require(n!=null && n.isObject()); Set<String> actual=new HashSet<>(); n.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected));
    }
    public static String quote(String s) {
        scalars(s); StringBuilder out=new StringBuilder("\"");
        s.codePoints().forEach(cp->{
            if(cp<32) out.append(String.format(Locale.ROOT,"\\u%04x",cp));
            else { if(cp==34 || cp==92) out.append('\\'); out.appendCodePoint(cp); }
        }); return out.append('"').toString();
    }
    public static void require(boolean ok) { if(!ok) throw new IllegalArgumentException("Invalid access contract"); }
}
