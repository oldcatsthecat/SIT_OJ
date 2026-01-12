package org.example.judge.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.judge.config.JudgeConfig;
import org.example.judge.constants.JudgeConstants;
import org.example.common.dto.JudgeResultItem;
import org.example.common.dto.JudgeResultResponse;
import org.example.common.dto.JudgeServerRequest;
import org.example.common.dto.JudgeServerResponse;
import org.example.judge.feign.ProblemFeignClient;
import org.example.judge.feign.SubmissionFeignClient;
import org.example.judge.service.JudgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JudgeServiceImpl implements JudgeService {

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private JudgeConfig judgeConfig;
    @Autowired
    private ProblemFeignClient problemFeignClient;
    @Autowired
    private SubmissionFeignClient submissionFeignClient;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public JudgeResultResponse processJudge(Integer submissionId) {
        try {
            // 1. 数据准备 (内部私有方法实现)
            Map<String, Object> submission = fetchSubmission(submissionId);
            Integer problemId = (Integer) submission.get("problemId");
            Map<String, Object> problem = fetchProblem(problemId);

            // 2. 提取参数
            String code = (String) submission.get("codeContent");
            String language = (String) submission.get("language");
            Integer timeLimit = Integer.parseInt(String.valueOf(problem.get("timeLimit")));
            Integer memoryLimit = Integer.parseInt(String.valueOf(problem.get("memoryLimit")));

            // 3. 执行物理判题
            JudgeServerResponse<Object> body = sendToJudgeServer(code, language, String.valueOf(problemId), timeLimit, memoryLimit);

            // 4. 解析判题机结果并转换 (逻辑下沉)
            return parseJudgeResponse(body, timeLimit);

        } catch (Exception e) {
            return JudgeResultResponse.builder().status("SE").errorMessage(e.getMessage()).build();
        }
    }

    /**
     * 解析判题机返回的复杂 JSON
     */
    private JudgeResultResponse parseJudgeResponse(JudgeServerResponse<Object> body, Integer timeLimit) throws Exception {
        if (body == null) return JudgeResultResponse.builder().status("SE").errorMessage("判题机无响应").build();

        // 编译错误处理
        if (body.getErr() != null) {
            String status = "CompileError".equals(body.getErr()) ? "CE" : "SE";
            return JudgeResultResponse.builder().status(status).errorMessage(String.valueOf(body.getData())).build();
        }

        // 测试点解析
        List<JudgeResultItem> results = objectMapper.convertValue(body.getData(), new TypeReference<List<JudgeResultItem>>() {});

        int maxTime = 0;
        long maxMemory = 0;
        String finalStatus = "AC";

        for (JudgeResultItem item : results) {

//            System.out.println("测试点结果代号: " + item.getResult());
//            System.out.println("测试用例号：" + item.getTest_case());
//            System.out.println("signal:" + item.getSignal());
//            System.out.println("error:" + item.getError());


            maxTime = Math.max(maxTime, item.getCpu_time() != null ? item.getCpu_time() : 0);
            maxMemory = Math.max(maxMemory, item.getMemory() != null ? item.getMemory() : 0);

            if (item.getResult() != 0 && "AC".equals(finalStatus)) {
                finalStatus = translateResult(item.getResult());
            }
            // 强制 TLE 补偿
            if (maxTime > timeLimit && "AC".equals(finalStatus)) finalStatus = "TLE";
        }

        return JudgeResultResponse.builder()
                .status(finalStatus)
                .timeCost(maxTime)
                .memoryCost((int) (maxMemory / 1024))
                .judgeInfo(objectMapper.writeValueAsString(results))
                .build();
    }

    private Map<String, Object> fetchSubmission(Integer id) {
        Object obj = submissionFeignClient.getSubmissionById(id);
        if (obj == null) throw new RuntimeException("提交记录丢失");
        return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> fetchProblem(Integer id) {
        Object obj = problemFeignClient.getProblemById(id);
        if (obj == null) throw new RuntimeException("题目信息丢失");
        return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }
    @Override
    public JudgeServerResponse<Object> sendToJudgeServer(String code, String language, String testCaseId, Integer timeLimit, Integer memoryLimit) {
        // 1. 生成加密 Token
        String token = DigestUtils.sha256Hex(judgeConfig.getToken());

        // 2. 动态获取语言配置 (处理 Java 的 -Xmx)
        Map<String, Object> langConfig = getDynamicLangConfig(language, memoryLimit);

        // 3. 设置运行限制
        int finalTime = (timeLimit != null) ? timeLimit : 1000;
        long finalMemory ;
        if (language.toUpperCase().contains("PYTHON")) {
            finalMemory = (long) (memoryLimit + 512) * 1024 * 1024;
        } else if(language.toUpperCase().contains("JAVA")){
            finalMemory = (long) (memoryLimit + 256) * 1024 * 1024;
        }
        else finalMemory = (long) memoryLimit * 1024 * 1024;

        System.out.println("最终内存为:" + finalMemory);

        // 4. 构建请求
        JudgeServerRequest request = JudgeServerRequest.builder()
                .src(code)
                .test_case_id(testCaseId)
                .max_cpu_time(finalTime)
                .max_memory((int) finalMemory)
                .language_config(langConfig)
                .output(false)//这里改了
                .build();

        // 5. 设置 Header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Judge-Server-Token", token);
        HttpEntity<JudgeServerRequest> entity = new HttpEntity<>(request, headers);

        // 6. URL 拼接处理 (自动去重 /judge)
        String baseUrl = judgeConfig.getServerUrl().trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String finalUrl = baseUrl + (baseUrl.endsWith("/judge") ? "" : "/judge");

        try {
            ResponseEntity<JudgeServerResponse<Object>> response = restTemplate.exchange(
                    finalUrl, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<JudgeServerResponse<Object>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            return JudgeServerResponse.builder().err("SystemError").data(e.getMessage()).build();
        }
    }

    private Map<String, Object> getDynamicLangConfig(String language, Integer memoryLimit) {
        if (language == null) return new HashMap<>(JudgeConstants.CPP_CONFIG_OBJECT);

        String lang = language.toUpperCase();

        // 1. 处理 C/C++
        if (lang.contains("C") || lang.contains("CPP")) {
            return new HashMap<>(JudgeConstants.CPP_CONFIG_OBJECT);
        }

        // 2. 处理 Python (新增部分)
        else if (lang.contains("PYTHON") || lang.contains("PY")) {
            // Python 配置相对固定，直接返回常量副本
            return new HashMap<>(JudgeConstants.PYTHON_CONFIG_OBJECT);
        }

        // 3. 处理 Java
        else if (lang.contains("JAVA")) {
            Map<String, Object> base = new HashMap<>(JudgeConstants.JAVA_CONFIG_OBJECT);
            // 深度复制 run 配置以防污染常量
            Map<String, Object> run = new HashMap<>((Map<String, Object>) base.get("run"));
            String cmd = (String) run.get("command");

            // Java 内存管理：JVM 堆内存通常设为物理限制的 80%
            int jvmHeap = (memoryLimit != null) ? (int)(memoryLimit * 0.8) : 200;
            run.put("command", cmd.replace("-Xmx256M", "-Xmx" + jvmHeap + "M"));

            base.put("run", run);
            return base;
        }

        // 默认兜底使用 CPP 配置
        return new HashMap<>(JudgeConstants.CPP_CONFIG_OBJECT);
    }

    private String translateResult(int code) {
        switch (code) {
            case -1: return "WA";
            case 0 : return "AC";
            case 1:
            case 2:  return "TLE";
            case 3:  return "MLE";
            case 4:  return "RE";
            case 5:  return "SE";
            default: return "Unknown";
        }
    }
}