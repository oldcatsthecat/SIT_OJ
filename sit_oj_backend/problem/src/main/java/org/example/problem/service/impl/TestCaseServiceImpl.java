package org.example.problem.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.problem.config.TestcaseConfig;
import org.example.problem.entity.TestCaseInfo;
import org.example.problem.entity.TestCaseDetail;
import org.example.problem.service.TestCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class TestCaseServiceImpl implements TestCaseService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestcaseConfig testcaseConfig;

    @Override
    public void processAndSync(MultipartFile zipFile, String problemId, boolean spj) throws Exception {
        Path localBase = Paths.get(testcaseConfig.getLocalBasePath());
        Path problemPath = localBase.resolve(problemId).normalize();

        // 1. 彻底清理旧目录
        if (Files.exists(problemPath)) {
            try (Stream<Path> walk = Files.walk(problemPath)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        Files.createDirectories(problemPath);

        // 2. 平铺解压
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String fileName = Paths.get(entry.getName()).getFileName().toString();
                Path filePath = problemPath.resolve(fileName);
                Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }

        TestCaseInfo info = new TestCaseInfo();
        // 设置 spj 标识
        info.setSpj(spj);

        Map<String, TestCaseDetail> detailMap = new TreeMap<>((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (Exception e) {
                return a.compareTo(b);
            }
        });

        // 3. 扫描并标准化处理
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(problemPath, "*.in")) {
            for (Path inPath : stream) {
                String inFileName = inPath.getFileName().toString();
                String baseName = inFileName.substring(0, inFileName.lastIndexOf(".in"));
                Path outPath = problemPath.resolve(baseName + ".out");

                // --- 新增逻辑：对于 spj = true，仅设置 input 字段 ---
                if (spj) {
                    TestCaseDetail detail = new TestCaseDetail();
                    detail.setInput_name(inFileName);
                    detail.setInput_size(Files.size(inPath));
                    detailMap.put(baseName, detail);
                    System.out.println("[READY] SPJ Point: " + baseName);
                }
                // --- 原始逻辑：普通题目依然要求存在 .out ---
                else if (Files.exists(outPath)) {
                    TestCaseDetail detail = new TestCaseDetail();
                    detail.setInput_name(inFileName);
                    detail.setOutput_name(baseName + ".out");
                    detail.setInput_size(Files.size(inPath));

                    String rawContent = Files.readString(outPath, StandardCharsets.UTF_8);
                    String normalized = rawContent.replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
                    byte[] finalBytes = normalized.getBytes(StandardCharsets.UTF_8);

                    String finalMd5 = DigestUtils.md5Hex(finalBytes);
                    detail.setStripped_output_md5(finalMd5);
                    detail.setOutput_size((long) finalBytes.length);

                    Files.write(outPath, finalBytes);
                    detailMap.put(baseName, detail);
                    System.out.println("[READY] Point: " + baseName + " | MD5: " + finalMd5);
                }
            }
        }

        info.setTest_cases(detailMap);
        String jsonInfo = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(info);
        Files.write(problemPath.resolve("info"), jsonInfo.getBytes(StandardCharsets.UTF_8));

        //syncToRemote(problemPath, problemId);
    }

    private void syncToRemote(Path localPath, String problemId) throws IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(testcaseConfig.getRemoteHost());
        try {
            ssh.authPassword(testcaseConfig.getRemoteUser(), testcaseConfig.getRemotePassword());
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                String remoteDir = testcaseConfig.getRemotePath() + "/" + problemId;
                sftp.mkdirs(remoteDir);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(localPath)) {
                    for (Path path : stream) {
                        if (Files.isRegularFile(path)) {
                            String remoteFile = remoteDir + "/" + path.getFileName().toString();
                            sftp.put(new net.schmizz.sshj.xfer.FileSystemFile(path.toFile()), remoteFile);
                        }
                    }
                }
            }
        } finally {
            if (ssh.isConnected()) ssh.disconnect();
        }
    }
}