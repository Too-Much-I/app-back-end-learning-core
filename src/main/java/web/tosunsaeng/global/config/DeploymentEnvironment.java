package web.tosunsaeng.global.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/** Deployment identity is separate from local test fixtures and workload JWT environment names. */
@Component
public final class DeploymentEnvironment implements InitializingBean {
    public static final String PROPERTY = "app.deployment.environment";
    private final Environment environment;

    public DeploymentEnvironment(Environment environment) { this.environment = environment; }

    @Override public void afterPropertiesSet() {
        String target = environment.getProperty(PROPERTY, "");
        boolean ecs = environment.containsProperty("ECS_CONTAINER_METADATA_URI_V4")
                || environment.containsProperty("ECS_CONTAINER_METADATA_URI");
        if (target.isEmpty() && !ecs) return; // Preserve local/test tooling without a deployment target.
        if (!Set.of("test", "prod").contains(target)
                || !Arrays.equals(environment.getActiveProfiles(), new String[]{target})
                || !"jwt".equals(environment.getProperty("app.auth.mode"))) {
            throw new IllegalStateException("Deployment requires matching prod/test profile and JWT authentication");
        }
    }

    public static boolean restricted(Environment env) {
        // Retain old strict checks while older staging tasks drain; staging is not a deployment target.
        return env.acceptsProfiles(Profiles.of("staging", "prod"))
                || !env.getProperty(PROPERTY, "").isEmpty()
                || env.containsProperty("ECS_CONTAINER_METADATA_URI_V4")
                || env.containsProperty("ECS_CONTAINER_METADATA_URI");
    }

    public static final class Restricted implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return restricted(context.getEnvironment());
        }
    }

    public static final class IndexChecks implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !context.getEnvironment().acceptsProfiles(Profiles.of("test"))
                    || restricted(context.getEnvironment());
        }
    }
}
