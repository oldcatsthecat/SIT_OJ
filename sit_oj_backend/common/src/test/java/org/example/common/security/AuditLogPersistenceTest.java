package org.example.common.security;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AuditLogPersistenceTest {
    @TempDir Path directory;

    @Test void auditIsSeparateJsonAndSurvivesLoggerRestart() throws Exception {
        Path config = Path.of("../../ops/logback-spring.xml").toAbsolutePath().normalize();
        assertTrue(Files.exists(config));
        for (int i = 1; i <= 2; i++) {
            var context = new LoggerContext();
            context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
            context.putProperty("OJ_LOG_DIR", directory.toString());
            try {
                var loader = new JoranConfigurator();
                loader.setContext(context);
                loader.doConfigure(config.toFile());
                context.getLogger("OJAccessAudit").info("{\"requestId\":\"request-" + i + "\"}");
                assertTrue(context.getStatusManager().getCopyOfStatusList().stream()
                        .noneMatch(status -> status.getLevel() >= Status.WARN),
                        () -> context.getStatusManager().getCopyOfStatusList().toString());
                // The audit event must be on disk before shutdown, without a console prefix.
                var lines = Files.readAllLines(directory.resolve("audit.ndjson"));
                assertEquals(i, lines.size());
                assertEquals("request-" + i, new ObjectMapper().readTree(lines.get(i - 1)).get("requestId").asText());
                assertFalse(Files.readString(directory.resolve("application.log")).contains("request-"));
            } finally { context.stop(); }
        }
    }
}
