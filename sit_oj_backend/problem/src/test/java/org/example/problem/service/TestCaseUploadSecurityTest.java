package org.example.problem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.problem.config.TestcaseConfig;
import org.example.problem.service.impl.TestCaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class TestCaseUploadSecurityTest {
    @TempDir Path base;
    private TestCaseServiceImpl service;
    @BeforeEach void setup() throws Exception {
        service = new TestCaseServiceImpl();
        var config = new TestcaseConfig();
        config.setLocalBasePath(base.toString());
        ReflectionTestUtils.setField(service, "testcaseConfig", config);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        Files.createDirectories(base.resolve("22"));
        Files.writeString(base.resolve("22/info"), "existing-data");
    }
    private MockMultipartFile zip(String... names) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("test\r\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "data.zip", "application/zip", bytes.toByteArray());
    }
    private void assertOldDataPreserved() throws Exception {
        assertEquals("existing-data", Files.readString(base.resolve("22/info")));
        try (var entries = Files.list(base)) { assertEquals(1, entries.count()); }
    }
    @Test void invalidFileCannotDeleteOldData() throws Exception {
        var upload = zip("1.in", "1.out", "script.sh");
        assertThrows(IllegalArgumentException.class, () -> service.processAndSync(upload,"22",false));
        assertOldDataPreserved();
    }
    @Test void missingOutputCannotReplaceOldData() throws Exception {
        var upload=zip("1.in");
        assertThrows(IllegalArgumentException.class, () -> service.processAndSync(upload,"22",false));
        assertOldDataPreserved();
    }
    @Test void emptyArchiveCannotReplaceOldData() throws Exception {
        var upload=zip();
        assertThrows(IllegalArgumentException.class, () -> service.processAndSync(upload,"22",false));
        assertOldDataPreserved();
    }
    @Test void pathTraversalProblemIdRejected() throws Exception {
        var upload=zip("1.in","1.out");
        assertThrows(IllegalArgumentException.class, () -> service.processAndSync(upload,"../22",false));
        assertOldDataPreserved();
    }
    @Test void validUploadReplacesDataAfterValidation() throws Exception {
        service.processAndSync(zip("nested/case_1.in","nested/case_1.out"),"22",false);
        assertTrue(Files.readString(base.resolve("22/info")).contains("case_1.in"));
        assertEquals("test", Files.readString(base.resolve("22/case_1.out")));
        try (var entries = Files.list(base)) { assertEquals(1, entries.count()); }
    }
}
