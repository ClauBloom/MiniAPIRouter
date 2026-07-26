package com.miniapi.router.core.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoreCouplingArchitectureTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

    @Test
    void intentPackageDoesNotDependOnStreamingImplementation() throws IOException {
        assertThat(javaSources("com/miniapi/router/core/intent"))
                .noneMatch(source -> source.contains("com.miniapi.router.core.streaming"));
    }

    @Test
    void routingPipelineDoesNotDependOnConcreteStrategies() throws IOException {
        String source = Files.readString(MAIN_JAVA.resolve(
                "com/miniapi/router/core/routing/RoutePipeline.java"));

        assertThat(source)
                .doesNotContain("WeightStrategy")
                .doesNotContain("PriorityStrategy")
                .doesNotContain("RoundRobinStrategy")
                .doesNotContain("LeastConnStrategy");
    }

    private List<String> javaSources(String packagePath) throws IOException {
        try (var paths = Files.walk(MAIN_JAVA.resolve(packagePath))) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(this::readUnchecked)
                    .toList();
        }
    }

    private String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
