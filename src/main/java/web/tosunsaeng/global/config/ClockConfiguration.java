package web.tosunsaeng.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    // Existing qualifiers are aliases of the same singleton, not separate clocks.
    @Bean(name = {"applicationClock", "gradingClock", "userMergedClock", "userWithdrawnClock"})
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
