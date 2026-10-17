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
              <el-tag size="small">时间: {{ effectiveLimits.timeLimit }}ms</el-tag>
              <el-tag size="small" type="warning">内存: {{ effectiveLimits.memoryLimit }}MB</el-tag>
              <el-tag v-if="cid" type="danger" effect="plain">比赛模式</el-tag>
            </div>
            <p class="limit-hint">限制随所选语言显示。C/C++ 使用题目设置，Python / Java 为 2 倍，内存最高 512MB。</p>
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
            <el-select v-model="submitForm.language" @change="handleLanguageChange" style="width: 100px;">
              <el-option label="C++" value="cpp" />
              <el-option label="C" value="c" />
              <el-option label="Java" value="java" />
              <el-option label="Python" value="python" />
            </el-select>
            <el-radio-group v-model="editorMode" @change="handleEditorModeChange">
              <el-radio-button value="monaco" :disabled="editorStatus === 'failed'">高级编辑器</el-radio-button>
              <el-radio-button value="simple">简易版</el-radio-button>
            </el-radio-group>
            <el-button @click="handleViewRecords">查看记录</el-button>
          </div>
          <el-button type="primary" :loading="submitting" :disabled="editorMode === 'monaco' && editorStatus !== 'ready'" @click="handleSubmit">
            {{ cid ? '比赛提交' : '提交代码' }}
          </el-button>
        </div>
        <div class="editor-workspace">
          <div v-if="editorMode === 'monaco' && ['loading', 'slow'].includes(editorStatus)" class="editor-loading" role="status">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>高级编辑器加载中...</span>
            <el-button link type="primary" @click="editorMode = 'simple'">使用简易版输入代码</el-button>
          </div>
          <el-alert v-if="editorMode === 'simple' && editorStatus === 'failed'" class="editor-fallback-notice"
                    title="高级编辑器暂时不可用，仍可在简易版输入并提交代码" type="warning" :closable="false" />
          <el-alert v-if="editorMode === 'simple' && editorStatus === 'slow'" class="editor-fallback-notice"
                    title="高级编辑器仍在加载，简易版可以直接提交代码" type="info" :closable="false" />
          <textarea v-if="editorMode === 'simple'" v-model="code" class="simple-editor"
                    aria-label="代码输入框" placeholder="在这里粘贴或输入代码" spellcheck="false"></textarea>
          <div id="code-editor" v-show="editorMode === 'monaco' && editorStatus === 'ready'" class="monaco-box"></div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, computed} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {problemApi, submissionApi, competitionApi} from '@/api';
import {ElMessage} from 'element-plus';
import {Back, Loading} from '@element-plus/icons-vue';

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
const editorMode = ref('monaco');
const editorStatus = ref('loading');
const code = ref(languageSnippets.cpp);
const problem = ref({});
const leftWidth = ref(45);
const containerRef = ref(null);
let editor = null;
let monaco = null;
let unmounted = false;
let isDragging = false;
let editorLoadTimer = null;
let previousLanguage = 'cpp';

const submitForm = ref({
  problemId: route.params.id,
  language: 'cpp',
  codeContent: '',
  competitionId: cid ? Number(cid) : null
});

const effectiveLimits = computed(() => {
  const doubled = ['python', 'java'].includes(submitForm.value.language);
  const timeLimit = problem.value.timeLimit ?? 1000;
  const memoryLimit = problem.value.memoryLimit ?? 256;
  return {
    timeLimit: doubled ? timeLimit * 2 : timeLimit,
    memoryLimit: doubled ? Math.min(memoryLimit * 2, 512) : memoryLimit
  };
});

const handleLanguageChange = (l) => {
  const useTemplate = code.value === languageSnippets[previousLanguage];
  if (useTemplate) code.value = languageSnippets[l] || '';
  previousLanguage = l;
  if (editor) {
    monaco.editor.setModelLanguage(editor.getModel(), l === 'cpp' || l === 'c' ? 'cpp' : l);
    if (useTemplate) editor.setValue(code.value);
  }
};

const mountEditor = () => {
  if (editor) {
    if (editor.getValue() !== code.value) editor.setValue(code.value);
    editor.layout();
    return;
  }
  const dom = document.getElementById('code-editor');
  if (!dom || unmounted) return;
  editor = monaco.editor.create(dom, {
    value: code.value,
    language: submitForm.value.language === 'c' ? 'cpp' : submitForm.value.language,
    theme: 'vs-dark',
    automaticLayout: true,
    fontSize: 14,
    fontFamily: "'Fira Code', 'Courier New', monospace",
    minimap: {enabled: false}
  });
  editor.onDidChangeModelContent(() => { code.value = editor.getValue(); });
};

const failEditor = () => {
  if (unmounted || !['loading', 'slow'].includes(editorStatus.value)) return;
  const wasSlow = editorStatus.value === 'slow';
  editorStatus.value = 'failed';
  editorMode.value = 'simple';
  if (!wasSlow) ElMessage.warning('高级编辑器加载失败，已切换到简易版');
};

const slowEditor = () => {
  if (unmounted || editorStatus.value !== 'loading') return;
  editorStatus.value = 'slow';
  editorMode.value = 'simple';
  ElMessage.warning('高级编辑器加载较慢，已切换到简易版');
};

const handleEditorModeChange = async (mode) => {
  if (mode !== 'monaco' || editorStatus.value !== 'ready') return;
  await nextTick();
  try { mountEditor(); }
  catch { editorStatus.value = 'failed'; editorMode.value = 'simple'; }
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

  if (!code.value.trim()) return ElMessage.warning("代码不能为空");

  submitting.value = true;

  try {
    submitForm.value.codeContent = code.value;

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

const initEditor = async () => {
  editorLoadTimer = setTimeout(slowEditor, 30000);
  try {
    const loaded = await import('monaco-editor');
    if (unmounted || !['loading', 'slow'].includes(editorStatus.value)) return;
    monaco = loaded;
    editorStatus.value = 'ready';
    if (editorMode.value === 'monaco') await handleEditorModeChange('monaco');
  } catch {
    failEditor();
  } finally {
    clearTimeout(editorLoadTimer);
  }
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
    const res = await problemApi.getById(route.params.id, cid ? Number(cid) : undefined);
    problem.value = res.data || res;
    await nextTick();
  } finally {
    pageLoading.value = false;
    void initEditor();
  }
});
onBeforeUnmount(() => {
  unmounted = true;
  clearTimeout(editorLoadTimer);
  if (editor) editor.dispose();
});
</script>

<style scoped>
.limit-hint {
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}

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
.scrollable :deep(.el-card__body) {
  padding-bottom: 10px;
}

.editor-workspace {
  flex: 1;
  min-height: 300px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.monaco-box, .simple-editor, .editor-loading {
  flex: 1;
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  min-height: 300px;
  box-sizing: border-box;
}

.editor-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12px;
  background: #f8f9fb;
  color: #606266;
}

.editor-loading .el-icon { font-size: 28px; color: #409eff; animation: editor-spin 1s linear infinite; }
@keyframes editor-spin { to { transform: rotate(360deg); } }
.editor-fallback-notice { flex: none; }
.simple-editor {
  padding: 16px;
  resize: none;
  outline: none;
  font: 14px/1.6 'Fira Code', 'Courier New', monospace;
  color: #303133;
  background: #fff;
}

.editor-header {
  padding: 8px;
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #dcdfe6;
}

.left-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.left-tools > .el-button {
  margin-left: 0;
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
.detail-section:last-child .markdown-body {
  margin-bottom: 0;
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
