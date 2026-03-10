package org.example.problem.service;

import org.springframework.web.multipart.MultipartFile;

public interface TestCaseService {

    void processAndSync(MultipartFile zipFile, String problemId ,boolean spj) throws Exception;
}