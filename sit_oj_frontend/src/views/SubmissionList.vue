<template>
  <div class="submission-list-container">
    <el-card class="box-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><List /></el-icon>
            <span class="header-title">提交记录</span>
          </div>

          <div class="filter-form">
            <el-input
                v-model="filters.problemId"
                placeholder="搜索题目 ID"
                clearable
                class="filter-input"
                @clear="handleFilter"
                @keyup.enter="handleFilter"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="handleFilter" class="action-btn">查询</el-button>
            <el-button @click="resetFilter" class="action-btn">重置</el-button>
          </div>
        </div>
      </template>

      <el-table
          :data="submissions"
          v-loading="loading && submissions.length === 0"
          element-loading-text="加载中..."
          class="custom-table"
          header-cell-class-name="table-header-cell"
      >
        <el-table-column label="运行 ID" width="100">
          <template #default="{ row }">
            <span class="mono-font id-text">#{{ row.submissionId }}</span>
          </template>
        </el-table-column>

        <el-table-column label="题目" min-width="220">
          <template #default="{ row }">
            <div class="problem-info">
              <el-tag size="small" type="info" class="problem-id-tag">P{{ row.problemId }}</el-tag>
              <el-link
                  type="primary"
                  class="problem-link"
                  :underline="false"
                  @click="goToProblem(row.problemId)"
              >
                {{ row.problemName || '未知题目' }}
              </el-link>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="提交者" width="140">
          <template #default="{ row }">
            <div class="user-info">
              <el-avatar :size="24" icon="UserFilled" class="user-avatar" />
              <span class="username-text">{{ row.username || '未知用户' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :class="['status-tag', statusClass(row.status)]" effect="dark">
              <el-icon v-if="isProcessing(row.status)" class="is-loading"><Loading /></el-icon>
              <span>{{ row.status || 'Unknown' }}</span>
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            <div class="resource-usage">
              <el-icon><Timer /></el-icon>
              <span>{{ row.timeCost ?? 0 }}ms</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="内存" width="110">
          <template #default="{ row }">
            <div class="resource-usage">
              <el-icon><Cpu /></el-icon>
              <span>{{ row.memoryCost ?? 0 }}KB</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="语言" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="warning" plain class="lang-tag">{{ row.language }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">
            <span class="time-text">{{ formatDate(row.submissionTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="详情" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
                v-if="row.canSeeDetail"
                circle
                size="small"
                icon="View"
                type="primary"
                plain
                @click="goToDetail(row.submissionId)"
            />
            <el-icon v-else style="color: #C0C4CC"><Lock /></el-icon>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            background
            layout="total, prev, pager, next, jumper"
            @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, reactive, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { submissionApi } from '@/api';
import { Search, List, UserFilled, Timer, Cpu, View, Lock, Loading } from '@element-plus/icons-vue';

const router = useRouter();
const route = useRoute();
const loading = ref(false);
const submissions = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(15);
const pollTimer = ref(null);

const filters = reactive({
  problemId: ''
});

const isProcessing = (status) => {
  const s = String(status || '').toUpperCase();
  return s === 'PENDING' || s === 'JUDGING';
};

const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return dateStr;
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  const h = String(date.getHours()).padStart(2, '0');
  const min = String(date.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${d} ${h}:${min}`;
};

const fetchSubmissions = async (isSilent = false) => {
  if (!isSilent) loading.value = true;
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      problemId: filters.problemId || undefined
    };
    const res = await submissionApi.getList(params);
    if (res && res.code === 200) {
      submissions.value = res.data.records || [];
      total.value = res.data.total || 0;
      checkNeedPolling();
    }
  } catch (err) {
    console.error("请求记录失败:", err);
  } finally {
    if (!isSilent) loading.value = false;
  }
};

const checkNeedPolling = () => {
  const needPolling = submissions.value.some(row => isProcessing(row.status));
  if (needPolling) {
    if (!pollTimer.value) {
      pollTimer.value = setInterval(() => {
        fetchSubmissions(true);
      }, 2000);
    }
  } else {
    stopPolling();
  }
};

const stopPolling = () => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value);
    pollTimer.value = null;
  }
};

const handleFilter = () => {
  currentPage.value = 1;
  stopPolling();
  fetchSubmissions();
};

const handlePageChange = (page) => {
  currentPage.value = page;
  stopPolling();
  fetchSubmissions();
};

const resetFilter = () => {
  filters.problemId = '';
  router.replace({ query: {} });
  handleFilter();
};

const goToDetail = (id) => {
  router.push(`/submissions/${id}`);
};

const goToProblem = (id) => {
  router.push(`/problem/${id}`);
};

const statusClass = (status) => {
  const s = String(status || '').toUpperCase();
  if (s === 'AC' || s === 'ACCEPTED') return 'status-ac';
  if (s === 'WA' || s === 'WRONG ANSWER') return 'status-wa';
  if (s === 'CE' || s === 'COMPILE ERROR') return 'status-ce';
  if (s === 'TLE' || s === 'TIME LIMIT EXCEEDED') return 'status-tle';
  if (s === 'MLE' || s === 'MEMORY LIMIT EXCEEDED') return 'status-mle';
  if (s === 'RE' || s === 'RUNTIME ERROR') return 'status-re';
  if (s === 'PENDING' || s === 'JUDGING') return 'status-pending';
  return 'status-other';
};

watch(
    () => route.query.problemId,
    (newVal) => {
      const valStr = newVal ? String(newVal) : '';
      if (filters.problemId !== valStr) {
        filters.problemId = valStr;
        currentPage.value = 1;
        stopPolling();
        fetchSubmissions();
      }
    }
);

onMounted(() => {
  if (route.query.problemId) {
    filters.problemId = String(route.query.problemId);
  }
  fetchSubmissions();
});

onBeforeUnmount(() => {
  stopPolling();
});
</script>

<style scoped>
.submission-list-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}
.box-card { border-radius: 12px; border: none; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { font-size: 20px; color: #409eff; }
.header-title { font-size: 18px; font-weight: 600; color: #303133; }
.filter-form { display: flex; gap: 10px; }
.filter-input { width: 200px; }
.custom-table { margin-top: 10px; }
.mono-font { font-family: 'Fira Code', monospace; font-weight: 600; }
.id-text { color: #909399; }
.problem-info { display: flex; align-items: center; gap: 8px; }
.problem-link { font-weight: 500; font-size: 15px; }
.user-info { display: flex; align-items: center; gap: 8px; }

/* 核心修正：利用深度选择器强制 el-tag 内部内容容器进行 Flex 布局 */
.status-tag {
  min-width: 100px;
  font-weight: bold;
  border: none;
}

/* 这里是关键：Element Plus 的文字其实包裹在一个 class 为 el-tag__content 的 span 里 */
.status-tag :deep(.el-tag__content) {
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  width: 100%;
}

/* 控制图标和文字的间距 */
.status-tag .el-icon {
  margin-right: 6px;
  flex-shrink: 0;
}

.status-ac { background-color: #67c23a !important; color: #fff; }
.status-wa { background-color: #f56c6c !important; color: #fff; }
.status-ce { background-color: #e6a23c !important; color: #fff; }
.status-tle, .status-mle { background-color: #909399 !important; color: #fff; }
.status-re { background-color: #9b59b6 !important; color: #fff; }
.status-pending { background-color: #409eff !important; color: #fff; }
.status-other { background-color: #dcdfe6 !important; color: #606266; }

.resource-usage { display: flex; align-items: center; gap: 4px; color: #606266; font-size: 13px; }
.time-text { color: #909399; font-size: 13px; }
.pagination-wrapper { margin-top: 30px; display: flex; justify-content: center; }
:deep(.table-header-cell) { background-color: #fafafa !important; color: #303133; font-weight: bold; }
:deep(.el-table__row) { height: 60px; }

/* 正在加载的旋转动画 */
.is-loading {
  animation: rotating 2s linear infinite;
  font-size: 14px;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>