<template>
  <div class="problem-detail" v-loading="pageLoading">
    <div class="split-container" ref="containerRef">
      <div class="left-panel" :style="{ width: leftWidth + '%' }">
        <el-card shadow="never" class="info-card scrollable">
          <div v-if="cid" class="comp-back-bar">
            <el-button link type="primary" @click="goBackToCompetition">
              <el-icon><Back /></el-icon> 返回题目列表
            </el-button>
          </div>

          <div class="problem-header">
            <h1 class="title-text">{{ problem.problemName }}</h1>
            <div class="meta-info">
              <el-tag size="small" type="info">ID: {{ problem.problemId }}</el-tag>
              <el-tag size="small" type="success">难度: {{ problem.difficulty }}</el-tag>
              <el-tag v-if="!cid" size="small">时间: {{ problem.timeLimit }}ms</el-tag>
              <el-tag v-if="!cid" size="small" type="warning">内存: {{ problem.memoryLimit }}MB</el-tag>
              <el-tag v-if="cid" type="danger" effect="plain">比赛模式</el-tag>
            </div>
          </div>
          <el-divider />

          <div class="detail-section">
            <h3 class="section-title">题目描述</h3>
            <div class="markdown-body" v-html="renderFullContent(problem.problemDescription)"></div>
          </div>

          <div class="detail-section" v-if="problem.inputDescription">
            <h3 class="section-title">输入格式</h3>
            <div class="markdown-body" v-html="renderFullContent(problem.inputDescription)"></div>
          </div>

          <div class="detail-section" v-if="problem.outputDescription">
            <h3 class="section-title">输出格式</h3>
            <div class="markdown-body" v-html="renderFullContent(problem.outputDescription)"></div>
          </div>

          <div class="detail-section" v-if="problem.samples && problem.samples.length > 0">
            <h3 class="section-title">样例</h3>
            <div v-for="(sample, index) in problem.samples" :key="index" class="sample-container">
              <el-row :gutter="20">
                <el-col :span="12">
                  <div class="sample-label">输入 #{{ index + 1 }}</div>
                  <pre class="sample-content">{{ sample.input }}</pre>
                </el-col>
                <el-col :span="12">
                  <div class="sample-label">输出 #{{ index + 1 }}</div>
                  <pre class="sample-content">{{ sample.output }}</pre>
                </el-col>
              </el-row>
            </div>
          </div>

          <div class="detail-section" v-if="problem.hint">
            <h3 class="section-title">提示</h3>
            <div class="markdown-body hint-box" v-html="renderFullContent(problem.hint)"></div>
          </div>
        </el-card>
      </div>

      <div class="resizer" @mousedown="startDrag">
        <div class="resizer-line"></div>
      </div>

      <div class="right-panel" :style="{ width: (100 - leftWidth) + '%' }">
        <div class="editor-header">
          <div class="left-tools">
            <el-select v-model="submitForm.language" @change="handleLanguageChange" style="width: 100px; margin-right: 10px;">
              <el-option label="C++" value="cpp" />
              <el-option label="C" value="c" />
              <el-option label="Java" value="java" />
              <el-option label="Python" value="python" />
            </el-select>
            <el-button @click="handleViewRecords">查看记录</el-button>
          </div>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ cid ? '比赛提交' : '提交代码' }}
          </el-button>
        </div>
        <div id="code-editor" class="monaco-box"></div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import * as monaco from 'monaco-editor';
import {problemApi, submissionApi, competitionApi} from '@/api';
import {ElMessage} from 'element-plus';
import {Back} from '@element-plus/icons-vue';
import {jwtDecode} from 'jwt-decode';

// --- Markdown & LaTeX 解析 ---
import MarkdownIt from 'markdown-it';
import katex from 'katex';
import 'katex/dist/katex.min.css';

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
});

const renderFullContent = (text) => {
  if (!text) return '';
  text = text.replace(/\$\$([\s\S]+?)\$\$/g, (match, formula) => {
    try {
      return `<div class="katex-display">${katex.renderToString(formula, {displayMode: true})}</div>`;
    } catch (e) {
      return match;
    }
  });
  text = text.replace(/\$([^\$\n]+?)\$/g, (match, formula) => {
    try {
      return katex.renderToString(formula, {displayMode: false});
    } catch (e) {
      return match;
    }
  });
  return md.render(text);
};

// --- 代码模板 ---
const languageSnippets = {
  cpp: '#include <iostream>\n\nusing namespace std;\n\nint main() {\n    int a, b;\n    while (cin >> a >> b) {\n        cout << a + b << endl;\n    }\n    return 0;\n}',
  c: '#include <stdio.h>\n\nint main() {\n    int a, b;\n    while (scanf("%d %d", &a, &b) != EOF) {\n        printf("%d\\n", a + b);\n    }\n    return 0;\n}',
  java: 'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.setProperty("file.encoding", "UTF-8"));\n        while (sc.hasNextInt()) {\n            int a = sc.nextInt();\n            int b = sc.nextInt();\n            System.out.println(a + b);\n        }\n    }\n}',
  python: 'import sys\n\nfor line in sys.stdin:\n    try:\n        a, b = map(int, line.split())\n        print(a + b)\n    except:\n        break'
};

const route = useRoute();
const router = useRouter();
const cid = route.query.cid;
const pageLoading = ref(false);
const submitting = ref(false);
const problem = ref({});
const leftWidth = ref(45);
const containerRef = ref(null);
let editor = null;
let isDragging = false;

const submitForm = ref({
  problemId: route.params.id,
  language: 'cpp',
  codeContent: '',
  userId: null,
  competitionId: cid ? Number(cid) : null
});

const handleLanguageChange = (l) => {
  if (!editor) return;
  const model = editor.getModel();
  monaco.editor.setModelLanguage(model, l === 'cpp' || l === 'c' ? 'cpp' : l);
  editor.setValue(languageSnippets[l] || '');
};

const handleViewRecords = () => {
  if (cid) {
    router.push({path: `/competition/${cid}`, query: {tab: 'submissions'}});
  } else {
    router.push({path: '/submissions', query: {problemId: problem.value.problemId}});
  }
};

/**
 * 核心逻辑修改：异步提交后跳转
 */
const handleSubmit = async () => {
  const token = localStorage.getItem('token');
  if (!token) return router.push('/login');

  const code = editor.getValue();
  if (!code.trim()) return ElMessage.warning("代码不能为空");

  submitting.value = true;

  try {
    const decoded = jwtDecode(token);
    submitForm.value.userId = Number(decoded.id);
    submitForm.value.codeContent = code;

    // 发起异步提交请求
    let res = cid ?
        await competitionApi.submit(cid, submitForm.value) :
        await submissionApi.submit(submitForm.value);

    if (res.code === 200) {
      ElMessage.success("提交成功，正在评测中...");

      // 延迟跳转，确保用户看到提示
      setTimeout(() => {
        if (cid) {
          // 跳转比赛详情页的提交记录标签页
          router.push({
            path: `/competition/${cid}`,
            query: { tab: 'submissions' }
          });
        } else {
          // 跳转全局提交记录页
          router.push({
            path: '/submissions',
            query: { problemId: problem.value.problemId }
          });
        }
      }, 500);
    } else {
      ElMessage.error(res.message || "提交失败");
    }
  } catch (err) {
    ElMessage.error("系统处理异常");
  } finally {
    submitting.value = false;
  }
};

const goBackToCompetition = () => {
  router.push({path: `/competition/${cid}`, query: {tab: 'problems'}});
};

const initEditor = () => {
  const dom = document.getElementById('code-editor');
  if (!dom) return;
  editor = monaco.editor.create(dom, {
    value: languageSnippets.cpp,
    language: 'cpp',
    theme: 'vs-dark',
    automaticLayout: true,
    fontSize: 14,
    fontFamily: "'Fira Code', 'Courier New', monospace",
    minimap: {enabled: false}
  });
};

const startDrag = () => {
  isDragging = true;
  document.addEventListener('mousemove', onDrag);
  document.addEventListener('mouseup', stopDrag);
};
const onDrag = (e) => {
  if (!isDragging) return;
  const rect = containerRef.value.getBoundingClientRect();
  let offset = ((e.clientX - rect.left) / rect.width) * 100;
  if (offset > 20 && offset < 80) leftWidth.value = offset;
  if (editor) editor.layout();
};
const stopDrag = () => {
  isDragging = false;
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', stopDrag);
};

onMounted(async () => {
  pageLoading.value = true;
  try {
    const res = await problemApi.getById(route.params.id);
    problem.value = res.data || res;
    await nextTick();
    initEditor();
  } finally {
    pageLoading.value = false;
  }
});
onBeforeUnmount(() => {
  if (editor) editor.dispose();
});
</script>

<style scoped>
/* 保持原有样式不变 */
.problem-detail {
  height: calc(100vh - 60px);
  padding: 10px;
  background: #f5f7fa;
}

.comp-back-bar {
  margin-bottom: 10px;
  border-bottom: 1px dashed #dcdfe6;
  padding-bottom: 5px;
}

.split-container {
  display: flex;
  height: 100%;
  width: 100%;
  overflow: hidden;
}

.left-panel {
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.right-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding-left: 5px;
}

.resizer {
  width: 8px;
  cursor: col-resize;
  display: flex;
  justify-content: center;
}

.resizer-line {
  width: 2px;
  height: 100%;
  background: #dcdfe6;
}

.scrollable {
  overflow-y: auto;
  height: 100%;
}

.monaco-box {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  min-height: 300px;
}

.editor-header {
  padding: 8px;
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #dcdfe6;
}

.section-title {
  border-left: 4px solid #409eff;
  padding-left: 10px;
  margin: 15px 0;
  font-size: 16px;
  font-weight: bold;
}

.markdown-body {
  line-height: 1.6;
  margin-bottom: 15px;
  color: #303133;
  font-size: 15px;
}

:deep(.katex-display) {
  margin: 10px 0;
  overflow-x: auto;
  overflow-y: hidden;
  text-align: center;
}

:deep(.katex) {
  font-size: 1.1em;
  line-height: 1.2;
  font-family: KaTeX_Main, Times New Roman, serif;
}

.hint-box {
  background-color: #fafafa;
  padding: 15px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.sample-container {
  background: #f8f9fa;
  padding: 10px;
  margin-bottom: 10px;
  border-radius: 4px;
}

.sample-content {
  background: #fff;
  padding: 8px;
  border: 1px solid #eee;
  font-family: 'Courier New', Courier, monospace;
  white-space: pre-wrap;
  margin-top: 5px;
}
</style>