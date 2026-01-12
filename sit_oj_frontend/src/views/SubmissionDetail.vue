<template>
  <div class="submission-detail-container">
    <el-card v-loading="loading && !submission.submissionId" class="detail-card" shadow="never">
      <template #header>
        <div class="detail-header">
          <div class="header-left">
            <el-button @click="router.back()" icon="ArrowLeft" plain size="small">返回</el-button>
            <span class="id-badge">#{{ submissionId }}</span>
            <span class="title">提交详情</span>
          </div>
          <el-tag :class="['status-tag', statusClass(submission.status)]" effect="dark">
            <el-icon v-if="isProcessing(submission.status)" class="is-loading"><Loading /></el-icon>
            <span class="status-text">{{ submission.status || 'JUDGING' }}</span>
          </el-tag>
        </div>
      </template>

      <div class="meta-info-grid">
        <div class="meta-item">
          <span class="label">题目 ID</span>
          <span class="val mono">P{{ submission.problemId ?? 0 }}</span>
        </div>
        <div class="meta-item">
          <span class="label">编程语言</span>
          <span class="val"><el-tag size="small" type="warning" plain>{{ submission.language }}</el-tag></span>
        </div>
        <div class="meta-item">
          <span class="label">最高运行时间</span>
          <span class="val mono">{{ submission.timeCost ?? 0 }} ms</span>
        </div>
        <div class="meta-item">
          <span class="label">最高运行内存</span>
          <span class="val mono">{{ submission.memoryCost ?? 0 }} KB</span>
        </div>
        <div class="meta-item">
          <span class="label">提交者 ID</span>
          <span class="val mono">{{ submission.userId }}</span>
        </div>
      </div>

      <div v-if="submission.errorMessage" class="error-container">
        <div class="section-title error-text">
          <el-icon><Warning /></el-icon> 诊断报告 / Diagnostic Report
        </div>
        <div class="error-box">
          <pre class="error-content">{{ submission.errorMessage }}</pre>
        </div>
      </div>

      <div v-else-if="judgeItems.length > 0" class="judge-info-section">
        <div class="section-title">
          <el-icon><List /></el-icon> 测试点详情 / Test Cases
        </div>
        <div class="test-cases-grid">
          <el-card
              v-for="(item, index) in judgeItems"
              :key="index"
              class="test-case-card"
              shadow="hover"
              :body-style="{ padding: '12px' }"
          >
            <div class="test-case-header">
              <span class="case-number">Case #{{ item.test_case }}</span>
              <el-tag
                  size="small"
                  :type="translateTestResult(item.result).type"
                  :class="item.result === 4 ? 're-tag' : ''"
                  effect="plain"
              >
                {{ translateTestResult(item.result).text }}
              </el-tag>
            </div>
            <div class="test-case-body">
              <div class="case-info"><span class="case-label">时间:</span> {{ item.cpu_time }}ms</div>
              <div class="case-info"><span class="case-label">内存:</span> {{ (item.memory / 1024).toFixed(0) }}KB</div>
            </div>
          </el-card>
        </div>
      </div>

      <div class="code-section">
        <div class="section-title">
          <el-icon><Document /></el-icon> 源代码 / Source Code
        </div>
        <div class="code-wrapper">
          <div class="code-header">
            <span class="lang-display">{{ submission.language }}</span>
            <el-button type="primary" link icon="CopyDocument" @click="copyCode">复制代码</el-button>
          </div>
          <pre><code :class="'language-' + (submission.language || 'cpp')">{{ submission.codeContent }}</code></pre>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import {ref, onMounted, onBeforeUnmount, nextTick} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {submissionApi} from '@/api';
import {ElMessage} from 'element-plus';
import {ArrowLeft, Warning, Document, CopyDocument, List, Loading} from '@element-plus/icons-vue';
import hljs from 'highlight.js';
import 'highlight.js/styles/atom-one-dark.css';

const route = useRoute();
const router = useRouter();
const submissionId = route.params.id;
const loading = ref(false);
const submission = ref({});
const judgeItems = ref([]);
const pollTimer = ref(null);

// 状态判断
const isProcessing = (status) => {
  const s = String(status || '').toUpperCase();
  return s === 'PENDING' || s === 'JUDGING';
};

// 映射 JudgeServer 的 result 代号
const translateTestResult = (resultCode) => {
  const map = {
    0: {text: 'Accepted', type: 'success'},
    '-1': {text: 'Wrong Answer', type: 'danger'},
    1: {text: 'Time Limit Exceeded', type: 'warning'},
    2: {text: 'Real Time Limit Exceeded', type: 'warning'},
    3: {text: 'Memory Limit Exceeded', type: 'warning'},
    4: {text: 'Runtime Error', type: ''},
    5: {text: 'System Error', type: 'info'}
  };
  return map[resultCode] || {text: 'Unknown', type: 'info'};
};

const stopPolling = () => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value);
    pollTimer.value = null;
  }
};

const fetchDetail = async (isSilent = false) => {
  if (!isSilent) loading.value = true;
  try {
    const res = await submissionApi.getById(submissionId);
    if (res.code === 200) {
      submission.value = res.data;

      // 解析测试点
      if (submission.value.judgeInfo) {
        try {
          const rawItems = JSON.parse(submission.value.judgeInfo);
          judgeItems.value = rawItems.sort((a, b) => parseInt(a.test_case) - parseInt(b.test_case));
        } catch (e) {
          console.error("解析 judgeInfo 失败", e);
        }
      }

      // 高亮代码
      nextTick(() => {
        document.querySelectorAll('pre code').forEach((el) => {
          hljs.highlightElement(el);
        });
      });

      // 修正 2: 详情页轮询逻辑
      if (isProcessing(submission.value.status)) {
        if (!pollTimer.value) {
          pollTimer.value = setInterval(() => fetchDetail(true), 2000);
        }
      } else {
        stopPolling();
      }
    } else {
      ElMessage.error(res.message || "无权查看或记录不存在");
      router.push('/submissions'); // 修正路径
    }
  } catch (err) {
    console.error(err);
    ElMessage.error("获取详情失败");
    stopPolling();
  } finally {
    if (!isSilent) loading.value = false;
  }
};

const copyCode = () => {
  navigator.clipboard.writeText(submission.value.codeContent);
  ElMessage.success("代码已复制到剪贴板");
};

const statusClass = (status) => {
  const s = String(status || '').toUpperCase();
  if (s === 'AC' || s === 'ACCEPTED') return 'status-ac';
  if (s === 'WA' || s === 'WRONG ANSWER') return 'status-wa';
  if (s === 'CE' || s === 'COMPILE ERROR') return 'status-ce';
  if (s === 'RE' || s === 'RUNTIME ERROR') return 'status-re';
  if (s === 'PENDING' || s === 'JUDGING') return 'status-pending';
  return 'status-other';
};

onMounted(fetchDetail);
onBeforeUnmount(stopPolling);
</script>

<style scoped>
.submission-detail-container {
  padding: 20px;
  background-color: #f8f9fa;
  min-height: calc(100vh - 60px);
}

.detail-card {
  max-width: 1000px;
  margin: 0 auto;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.id-badge {
  background: #f0f2f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: monospace;
  font-weight: bold;
  color: #606266;
}

.title {
  font-size: 18px;
  font-weight: 600;
}

/* 修正 3: 状态标签 Flex 居中对齐，与列表页保持一致 */
.status-tag {
  min-width: 110px;
  height: 32px;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  border: none;
}

:deep(.el-tag__content) {
  display: flex !important;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.status-ac { background-color: #67c23a !important; }
.status-wa { background-color: #f56c6c !important; }
.status-ce { background-color: #e6a23c !important; }
.status-re { background-color: #9b59b6 !important; }
.status-pending { background-color: #409eff !important; }
.status-other { background-color: #909399 !important; }

/* 旋转动画 */
.is-loading {
  animation: rotating 2s linear infinite;
  font-size: 14px;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

:deep(.re-tag) {
  color: #9b59b6 !important;
  background-color: #f5eef8 !important;
  border-color: #dcbfe6 !important;
}

/* ... 保持原有其余样式不变 ... */
.meta-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 20px;
  padding: 20px;
  background: #fff;
  border: 1px solid #f0f2f5;
  border-radius: 8px;
  margin-bottom: 25px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-item .label {
  font-size: 12px;
  color: #909399;
  text-transform: uppercase;
}

.meta-item .val {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.mono {
  font-family: 'Fira Code', 'Courier New', monospace;
}

.judge-info-section {
  margin-bottom: 30px;
}

.test-cases-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.test-case-card {
  border: 1px solid #f0f2f5;
  background-color: #fafafa;
}

.test-case-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e4e7ed;
}

.case-number {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
}

.test-case-body {
  font-size: 12px;
  color: #909399;
}

.case-info {
  margin-bottom: 4px;
}

.case-label {
  color: #c0c4cc;
  margin-right: 4px;
}

.error-container {
  margin-bottom: 30px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.error-text {
  color: #f56c6c;
}

.error-box {
  background-color: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 6px;
  padding: 15px;
}

.error-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Fira Code', monospace;
  font-size: 13px;
  color: #cf1322;
  line-height: 1.6;
}

.code-wrapper {
  background: #282c34;
  border-radius: 8px;
  overflow: hidden;
}

.code-header {
  background: #21252b;
  padding: 8px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #181a1f;
}

.lang-display {
  color: #abb2bf;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

pre {
  margin: 0;
  padding: 15px;
  max-height: 500px;
  overflow: auto;
}

code {
  font-family: 'Fira Code', 'Source Code Pro', monospace !important;
  font-size: 14px !important;
  line-height: 1.5;
}
</style>