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
    public void processAndSync(MultipartFile zipFile, String problemId) throws Exception {
        Path localBase = Paths.get(testcaseConfig.getLocalBasePath());
        Path problemPath = localBase.resolve(problemId).normalize();

        // 1. 彻底清理旧目录，防止 baseName 冲突或残留
        if (Files.exists(problemPath)) {
            try (Stream<Path> walk = Files.walk(problemPath)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        Files.createDirectories(problemPath);

        // 2. 平铺解压：不管压缩包里有没有文件夹，全部解压到 problemPath 根下
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                // 只取文件名部分，去除路径
                String fileName = Paths.get(entry.getName()).getFileName().toString();
                Path filePath = problemPath.resolve(fileName);
                Files.copy(zis, filePath, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }

        TestCaseInfo info = new TestCaseInfo();
        // 使用 TreeMap 配合自定义排序，让 info 里的测试点按 1, 2, 3... 顺序排列
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

                if (Files.exists(outPath)) {
                    TestCaseDetail detail = new TestCaseDetail();
                    detail.setInput_name(inFileName);
                    detail.setOutput_name(baseName + ".out");
                    detail.setInput_size(Files.size(inPath));

                    // --- 标准化核心逻辑 ---
                    String rawContent = Files.readString(outPath, StandardCharsets.UTF_8);
                    // 统一换行符并保留唯一的一个末尾换行
                    String normalized = rawContent.replace("\r\n", "\n").replace("\r", "\n").stripTrailing() ;
                    byte[] finalBytes = normalized.getBytes(StandardCharsets.UTF_8);

                    // 计算 MD5
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

        syncToRemote(problemPath, problemId);
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