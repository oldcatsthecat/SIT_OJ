package org.example.judge.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.common.dto.*;
import org.example.judge.config.JudgeConfig;
import org.example.judge.constants.JudgeConstants;
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
import java.util.Locale;
import java.util.Map;

@Slf4j
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

    /**
     * RabbitMQ 消息入口：直接从 JudgeMessage 判题（无需 Feign 回调查询）
     * 返回 JudgeResultMessage 供结果队列使用
     */
    @Override
    public JudgeResultMessage processJudge(JudgeMessage message) {
        try {
            // 1. 获取题目信息
            Map<String, Object> problem = fetchProblem(message.getProblemId());

            // 2. 提取参数
            String code = message.getCodeContent();
            String language = message.getLanguage();
            Integer timeLimit = Integer.parseInt(String.valueOf(problem.get("timeLimit")));
            Integer memoryLimit = Integer.parseInt(String.valueOf(problem.get("memoryLimit")));
            Integer judgeType = Integer.parseInt(String.valueOf(problem.get("judgeType")));

            // 3. 执行判题
            JudgeServerResponse<Object> body;
            if (judgeType == 0) {
                body = sendToJudgeServer(code, language, String.valueOf(message.getProblemId()), timeLimit, memoryLimit);
            } else {
                String spjCode = (String) problem.get("spjCode");
                body = sendToJudgeServerSpj(code, language, String.valueOf(message.getProblemId()), timeLimit, memoryLimit, spjCode);
            }

            // 4. 解析判题结果
            JudgeResultResponse judgeResult = parseJudgeResponse(body, getEffectiveTimeLimit(language, timeLimit));

            // 5. 构建消息结果
            return JudgeResultMessage.builder()
                    .submissionId(message.getSubmissionId())
                    .userId(message.getUserId())
                    .problemId(message.getProblemId())
                    .competitionId(message.getCompetitionId())
                    .status(judgeResult.getStatus())
                    .timeCost(judgeResult.getTimeCost())
                    .memoryCost(judgeResult.getMemoryCost())
                    .judgeInfo(judgeResult.getJudgeInfo())
                    .errorMessage(judgeResult.getErrorMessage())
                    .build();

        } catch (Exception e) {
            log.error("判题异常: submissionId={}", message.getSubmissionId(), e);
            return JudgeResultMessage.builder()
                    .submissionId(message.getSubmissionId())
                    .userId(message.getUserId())
                    .problemId(message.getProblemId())
                    .competitionId(message.getCompetitionId())
                    .status("SE")
                    .errorMessage("判题异常: " + e.getMessage())
                    .build();
        }
    }

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
            Integer judge_type = Integer.parseInt(String.valueOf(problem.get("judgeType")));

            // 3. 执行物理判题
            JudgeServerResponse<Object> body ;
            if(judge_type==0) body = sendToJudgeServer(code, language, String.valueOf(problemId), timeLimit, memoryLimit);
            else
            {
                String spj_code = (String) problem.get("spjCode");
                body = sendToJudgeServerSpj(code, language, String.valueOf(problemId), timeLimit, memoryLimit,spj_code);
            }
            // 4. 解析判题机结果并转换 (逻辑下沉)
            return parseJudgeResponse(body, getEffectiveTimeLimit(language, timeLimit));

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
            String errType = body.getErr();
            if ("CompileError".equals(errType)) {
                return JudgeResultResponse.builder().status("CE").errorMessage(String.valueOf(body.getData())).build();
            } else if ("SPJCompileError".equals(errType)) {
                return JudgeResultResponse.builder().status("SE").errorMessage("SPJ编译错误: " + body.getData()).build();
            } else {
                return JudgeResultResponse.builder().status("SE").errorMessage(errType + ": " + body.getData()).build();
            }
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

        // 2. 题目存储 C/C++ 基准限制，Java 的堆大小使用调整后的内存限制
        int finalTime = getEffectiveTimeLimit(language, timeLimit);
        int effectiveMemory = getEffectiveMemoryLimit(language, memoryLimit);
        Map<String, Object> langConfig = getDynamicLangConfig(language, effectiveMemory);

        // 3. 将 MB 转为 JudgeServer 要求的 byte
        long finalMemory = (long) effectiveMemory * 1024 * 1024;

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


    public JudgeServerResponse<Object> sendToJudgeServerSpj(String code, String language, String testCaseId, Integer timeLimit, Integer memoryLimit, String spj_src) {
        // 1. 生成加密 Token
        String token = DigestUtils.sha256Hex(judgeConfig.getToken());

        // 2. 普通判题与 SPJ 使用相同的用户程序资源限制
        int finalTime = getEffectiveTimeLimit(language, timeLimit);
        int effectiveMemory = getEffectiveMemoryLimit(language, memoryLimit);
        Map<String, Object> langConfig = getDynamicLangConfig(language, effectiveMemory);

        // 3. 将 MB 转为 byte；SPJ 检查器自身的资源由 JudgeServer 管理
        long finalMemory = (long) effectiveMemory * 1024 * 1024;

        // --- 准备 SPJ 必须的默认配置 (通常基于 C++ ) ---
        // spj_version 使用源码的 MD5，防止判题机缓存了旧的编译结果
        String spjVersion = DigestUtils.md5Hex(spj_src);

        // 1. 获取 C++ 基础配置（用于 SPJ 的编译和运行，因为 SPJ 是 C++ 程序）
        Map<String, Object> cppConfig = getDynamicLangConfig("C++", 256);

        // 2. 准备 spj_compile_config
        Map<String, Object> spjCompileConfig = new HashMap<>((Map<String, Object>) cppConfig.get("compile"));
        spjCompileConfig.put("src_name", "spj-{spj_version}.cpp");
        spjCompileConfig.put("exe_name", "spj-{spj_version}");
        // 判题机编译 SPJ 时使用 {exe_path} 占位符（= {exe_dir}/{exe_name}）
        spjCompileConfig.put("compile_command", "/usr/bin/g++ -DONLINE_JUDGE -O2 -std=c++17 {src_path} -lm -o {exe_path}");

        // 3. 准备 spj_config (SPJ 运行配置)
        Map<String, Object> spjRunConfig = new HashMap<>((Map<String, Object>) cppConfig.get("run"));
        spjRunConfig.put("exe_name", "spj-{spj_version}");
        // SPJ 接收三个参数：输入文件路径、用户输出文件路径
        spjRunConfig.put("command", "{exe_path} {in_file_path} {user_out_file_path}");

        // 4. 先显式调用 /compile_spj 预编译 SPJ（避免依赖 /judge 的 on-the-fly 编译）
        compileSpjOnServer(token, spj_src, spjVersion, spjCompileConfig);

        // 5. 构建 /judge 请求
        JudgeServerRequestSpj request = JudgeServerRequestSpj.builder()
                .src(code)
                .spj_src(spj_src)
                .max_cpu_time(finalTime)
                .max_memory((int) finalMemory)
                .language_config(langConfig)
                .spj_version(spjVersion)
                .spj_config(spjRunConfig)
                .spj_compile_config(spjCompileConfig)
                .output(false)
                .test_case_id(testCaseId)
                .build();

        // 6. 设置 Header
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Judge-Server-Token", token);
        HttpEntity<JudgeServerRequestSpj> entity = new HttpEntity<>(request, headers);

        // 7. URL 拼接处理
        String baseUrl = judgeConfig.getServerUrl().trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String finalUrl = baseUrl + (baseUrl.endsWith("/judge") ? "" : "/judge");

        System.out.println("发送 SPJ 请求至: " + finalUrl);

        try {
            ResponseEntity<JudgeServerResponse<Object>> response = restTemplate.exchange(
                    finalUrl, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<JudgeServerResponse<Object>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            System.err.println("调用判题机 SPJ 接口异常: " + e.getMessage());
            return JudgeServerResponse.builder().err("SystemError").data(e.getMessage()).build();
        }
    }

    /**
     * 显式调用 JudgeServer 的 /compile_spj 接口预编译 SPJ 程序
     * 这确保 SPJ 编译错误能被及时发现并返回明确的错误信息
     */
    private void compileSpjOnServer(String token, String spjSrc, String spjVersion, Map<String, Object> spjCompileConfig) {
        String baseUrl = judgeConfig.getServerUrl().trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String compileUrl = baseUrl + (baseUrl.endsWith("/compile_spj") ? "" : "/compile_spj");

        Map<String, Object> compileRequest = new HashMap<>();
        compileRequest.put("src", spjSrc);
        compileRequest.put("spj_version", spjVersion);
        compileRequest.put("spj_compile_config", spjCompileConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Judge-Server-Token", token);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(compileRequest, headers);

        System.out.println("预编译 SPJ: " + compileUrl + " version=" + spjVersion);

        try {
            ResponseEntity<JudgeServerResponse<Object>> response = restTemplate.exchange(
                    compileUrl, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<JudgeServerResponse<Object>>() {}
            );
            JudgeServerResponse<Object> body = response.getBody();
            if (body != null && body.getErr() != null) {
                System.err.println("SPJ 预编译失败: " + body.getErr() + " - " + body.getData());
            } else {
                System.out.println("SPJ 预编译成功");
            }
        } catch (Exception e) {
            System.err.println("SPJ 预编译请求异常（将回退到 /judge 的 on-the-fly 编译）: " + e.getMessage());
            // 不抛出异常，因为 /judge 接口内部也会尝试编译 SPJ
        }
    }

    /** 题目设置以 C/C++ 为基准，Python/Java 获得两倍限制，内存最多 512 MB。 */
    private boolean usesDoubledLimits(String language) {
        String lang = language == null ? "" : language.toUpperCase(Locale.ROOT);
        return lang.contains("PY") || lang.contains("JAVA");
    }

    private int getEffectiveTimeLimit(String language, Integer timeLimit) {
        int baseTime = timeLimit != null ? timeLimit : 1000;
        return usesDoubledLimits(language) ? Math.multiplyExact(baseTime, 2) : baseTime;
    }

    private int getEffectiveMemoryLimit(String language, Integer memoryLimit) {
        int baseMemory = memoryLimit != null ? memoryLimit : 256;
        return usesDoubledLimits(language) ? (int) Math.min((long) baseMemory * 2, 512) : baseMemory;
    }

    private Map<String, Object> getDynamicLangConfig(String language, Integer memoryLimit) {
        if (language == null) return new HashMap<>(JudgeConstants.CPP_CONFIG_OBJECT);

        String lang = language.toUpperCase(Locale.ROOT);

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
