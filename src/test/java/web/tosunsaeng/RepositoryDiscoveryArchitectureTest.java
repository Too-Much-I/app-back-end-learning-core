package web.tosunsaeng;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryDiscoveryArchitectureTest {
    @Test
    void bootOwnsMongoRepositoryDiscoveryWithoutFeatureSpecificRegistrars() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EnableMongoRepositories.class));

        // Explicit registrars make Boot back off, even when placed in another feature package.
        assertThat(scanner.findCandidateComponents(TosunsaengApplication.class.getPackageName()))
                .isEmpty();
    }
}
