package org.example.judge.constants;

import java.util.HashMap;
import java.util.Map;

public class JudgeConstants {

    public static final Map<String, Object> JAVA_CONFIG_OBJECT = new HashMap<String, Object>() {{
        put("compile", new HashMap<String, Object>() {{
            put("src_name", "Main.java");
            put("exe_name", "Main");
            put("max_cpu_time", 5000);
            put("max_real_time", 10000);
            put("max_memory", -1);
            put("compile_command", "/usr/bin/javac {src_path} -d {exe_dir} -encoding utf8");
        }});
        put("run", new HashMap<String, Object>() {{
            // 核心修改：添加了限制虚拟内存分配的 JVM 参数
            put("command", "/usr/bin/java -cp {exe_dir} " +
                    "-Xss8M " +
                    "-Xmx256M " +
                    "-XX:CompressedClassSpaceSize=64M " +
                    "-XX:MaxMetaspaceSize=64M " +
                    "-Djava.awt.headless=true Main");
            put("seccomp_rule", null);
            put("env", new String[]{"LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8"});
        }});
    }};

    public static final Map<String, Object> CPP_CONFIG_OBJECT = new HashMap<String, Object>() {{
        put("compile", new HashMap<String, Object>() {{
            put("src_name", "main.cpp");
            put("exe_name", "main");
            put("max_cpu_time", 10000);
            put("max_real_time", 20000);
            put("max_memory", 1024 * 1024 * 1024);
            put("compile_command", "/usr/bin/g++ -DONLINE_JUDGE -w -fmax-errors=3 -std=c++17 {src_path} -lm -o {exe_dir}/main");
        }});
        put("run", new HashMap<String, Object>() {{
            put("command", "{exe_dir}/main");
            put("seccomp_rule", "c_cpp");
            put("env", new String[]{"LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8"});
        }});
    }};

    public static final Map<String, Object> PYTHON_CONFIG_OBJECT = new HashMap<String, Object>() {{
        put("compile", new HashMap<String, Object>() {{
            put("src_name", "solution.py");
            put("exe_name", "solution.py"); // 编译生成的字节码文件名
            put("max_cpu_time", 3000);
            put("max_real_time", 5000);
            put("max_memory", 128 * 1024 * 1024);
            // 使用 python3 内置模块进行编译校验
            put("compile_command", "/usr/bin/python3 -m py_compile {src_path}");
        }});
        put("run", new HashMap<String, Object>() {{
            // 运行命令，注意对于 python3 来说，exe_path 指向的是源码或生成的字节码路径
            put("command", "/usr/bin/python3 {exe_path}");
            put("seccomp_rule", "general");  // 必须与 QingdaoOJ 原生配置一致
            put("env", new String[]{
                    "PYTHONIOENCODING=UTF-8",
                    "LANG=en_US.UTF-8",
                    "LANGUAGE=en_US:en",
                    "LC_ALL=en_US.UTF-8"
            });
        }});
    }};

}