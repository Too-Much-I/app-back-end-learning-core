package web.tosunsaeng.domain.answerlearning;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandSucceededEvent;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class LearningTimingMongoConfiguration {
    @Bean
    MongoClientSettingsBuilderCustomizer learningTimingMongoCustomizer() {
        return settings -> settings.addCommandListener(new TimingListener());
    }

    static final class TimingListener implements CommandListener {
        @Override public void commandSucceeded(CommandSucceededEvent event) {
            LearningRequestTiming.mongo(event.getElapsedTime(TimeUnit.NANOSECONDS));
        }
        @Override public void commandFailed(CommandFailedEvent event) {
            LearningRequestTiming.mongo(event.getElapsedTime(TimeUnit.NANOSECONDS));
        }
    }
}
