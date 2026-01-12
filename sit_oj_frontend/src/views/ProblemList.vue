<template>
  <div class="problem-list-container">
    <el-card class="box-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><Collection /></el-icon>
            <span class="header-title">题目列表</span>
          </div>

          <div class="header-right">
            <el-input
                v-model.number="searchId"
                placeholder="输入题目编号 (如: 1)"
                prefix-icon="Search"
                size="default"
                style="width: 220px"
                clearable
                @clear="handleSearch"
                @keyup.enter="handleSearch"
            >
              <template #prepend>P</template>
            </el-input>
            <el-button type="primary" @click="handleSearch" class="search-btn">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </div>
        </div>
      </template>

      <el-table
          :data="problems"
          v-loading="loading"
          style="width: 100%"
          class="custom-table"
          header-cell-class-name="table-header-cell"
      >
        <el-table-column label="ID" width="100">
          <template #default="scope">
            <span class="problem-id">P{{ scope.row.problemId }}</span>
          </template>
        </el-table-column>

        <el-table-column label="题目名称" min-width="250">
          <template #default="scope">
            <div style="display: flex; align-items: center; gap: 10px;">
              <el-link
                  type="primary"
                  class="problem-name-link"
                  :underline="false"
                  @click="goToDetail(scope.row.problemId)"
              >
                {{ scope.row.problemName }}
              </el-link>

              <el-tag
                  v-if="scope.row.isSolved"
                  type="success"
                  size="small"
                  effect="dark"
                  style="font-weight: bold; border: none; border-radius: 4px;"
              >
                已通过
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="difficulty" label="难度" width="120" align="center">
          <template #default="scope">
            <el-tag
                :class="['difficulty-tag', getDifficultyClass(scope.row.difficulty)]"
                effect="dark"
                size="small"
            >
              {{ scope.row.difficulty }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="通过情况" width="180" align="center">
          <template #default="scope">
            <div class="pass-info">
              <span class="numbers">{{ scope.row.acceptedNumber }} / {{ scope.row.submissionNumber }}</span>
              <el-progress
                  :percentage="calculateRate(scope.row.acceptedNumber, scope.row.submissionNumber)"
                  :stroke-width="4"
                  :show-text="false"
                  :status="calculateRate(scope.row.acceptedNumber, scope.row.submissionNumber) > 50 ? 'success' : ''"
              />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { problemApi } from '@/api';
import { ElMessage } from 'element-plus';
import { Collection, Search } from '@element-plus/icons-vue';

const router = useRouter();
const problems = ref([]);
const loading = ref(false);

// 搜索绑定的 ID
const searchId = ref('');

const loadProblems = async (id = null) => {
  loading.value = true;
  try {
    const queryParams = {};
    if (id !== null && id !== '') {
      queryParams.problemId = id;
    }

    const res = await problemApi.getList(queryParams);
    const rawData = res.data ? res.data : res;
    problems.value = Array.isArray(rawData) ? rawData : [];

    console.log("加载题目完成，带状态数据:", problems.value);
  } catch (error) {
    console.error("加载题目失败:", error);
    ElMessage.error("获取题目列表失败");
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  loadProblems(searchId.value);
};

const resetSearch = () => {
  searchId.value = '';
  loadProblems();
};

const goToDetail = (id) => {
  router.push(`/problem/${id}`);
};

const calculateRate = (ac, total) => {
  if (!total || total === 0) return 0;
  return Math.round((ac / total) * 100);
};

const getDifficultyClass = (d) => {
  const diffMap = { '简单': 'diff-easy', '中等': 'diff-medium', '困难': 'diff-hard' };
  return diffMap[d] || '';
};

onMounted(() => {
  loadProblems();
});
</script>

<style scoped>
.problem-list-container {
  max-width: 1100px;
  margin: 20px auto;
  padding: 0 20px;
}
.box-card { border-radius: 10px; border: none; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { font-size: 22px; color: #409eff; }
.header-title { font-size: 18px; font-weight: 700; color: #303133; }
.header-right { display: flex; gap: 10px; align-items: center; }

/* 搜索框美化 */
:deep(.el-input-group__prepend) {
  padding: 0 12px;
  background-color: #f5f7fa;
  color: #909399;
  font-weight: bold;
}

.problem-id { font-family: 'Fira Code', monospace; font-weight: 600; color: #909399; }
.problem-name-link { font-size: 16px; font-weight: 500; transition: all 0.3s; }
.problem-name-link:hover { color: #409eff; transform: translateX(4px); }

.difficulty-tag { border: none; min-width: 60px; justify-content: center; }
.diff-easy { background-color: #67c23a !important; }
.diff-medium { background-color: #e6a23c !important; }
.diff-hard { background-color: #f56c6c !important; }

.pass-info { display: flex; flex-direction: column; gap: 4px; padding: 0 10px; }
.numbers { font-size: 13px; color: #606266; font-family: 'Fira Code', monospace; }

:deep(.table-header-cell) { background-color: #f8f9fa !important; color: #303133; font-weight: bold; height: 50px; }
:deep(.el-table__row) { height: 65px; }
</style>