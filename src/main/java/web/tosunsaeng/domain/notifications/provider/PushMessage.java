package web.tosunsaeng.domain.notifications.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PushMessage {

    private final String expoPushToken;
    private final String sound;
    private final String channelId;
    private final String title;
    private final String body;
    private final Map<String, String> data;

    public PushMessage(
            String expoPushToken,
            String sound,
            String channelId,
            String title,
            String body,
            Map<String, String> data) {
        this.expoPushToken = expoPushToken;
        this.sound = sound;
        this.channelId = channelId;
        this.title = title;
        this.body = body;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public String expoPushToken() {
        return expoPushToken;
    }

    public String sound() {
        return sound;
    }

    public String channelId() {
        return channelId;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public Map<String, String> data() {
        return data;
    }

    @Override
    public String toString() {
        return "PushMessage{expoPushToken=[REDACTED], dataKeys=" + data.keySet() + "}";
    }
}
