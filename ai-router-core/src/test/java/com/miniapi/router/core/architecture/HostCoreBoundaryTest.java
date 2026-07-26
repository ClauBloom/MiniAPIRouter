package com.miniapi.router.core.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HostCoreBoundaryTest {

    @Test
    void hostServicesDoNotDependOnCoreInternals() throws IOException {
        for (Path service : hostBoundaryServices()) {
            String source = Files.readString(service);
            assertThat(source)
                    .as(service.toString())
                    .doesNotContain("com.miniapi.router.core.routing")
                    .doesNotContain("com.miniapi.router.core.streaming")
                    .doesNotContain("com.miniapi.router.core.protocol.ProtocolRegistry")
                    .doesNotContain("com.miniapi.router.core.protocol.converter");
        }
    }

    private List<Path> hostBoundaryServices() {
        Path root = Path.of("..");
        return List.of(
                root.resolve("ai-router-saas/src/main/java/com/miniapi/router/saas/service/ProxyService.java"),
                root.resolve("ai-router-standalone/src/main/java/com/miniapi/router/standalone/service/StandaloneProxyService.java"),
                root.resolve("ai-router-standalone/src/main/java/com/miniapi/router/standalone/service/ConfigService.java")
        );
    }
}
