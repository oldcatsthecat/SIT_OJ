<template>
  <div class="competition-list-container">
    <el-card class="box-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><Trophy /></el-icon>
            <span class="header-title">比赛列表</span>
          </div>
        </div>
      </template>

      <el-table
          :data="competitions"
          v-loading="loading"
          style="width: 100%"
          class="custom-table"
      >
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row)" effect="dark" class="status-tag">
              {{ getStatusText(row) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="比赛名称" min-width="200">
          <template #default="{ row }">
            <div class="name-container">
              <el-link
                  type="primary"
                  class="comp-name-link"
                  @click="goToDetail(row.competitionId)"
                  :underline="false"
              >
                {{ row.competitionName }}
              </el-link>
              <el-tag v-if="row.isRegistered || row.registered" size="small" type="success" effect="plain" class="reg-badge">
                已报名
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.startTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="持续时间" width="150">
          <template #default="{ row }">
            <span class="duration-text">{{ getDuration(row.startTime, row.endTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
                v-if="getStatusText(row) === '未开始' && !(row.isRegistered || row.registered)"
                type="success"
                size="small"
                plain
                @click="handleRegister(row.competitionId)"
            >
              立即报名
            </el-button>

            <el-button
                v-else-if="getStatusText(row) === '未开始' && (row.isRegistered || row.registered)"
                type="info"
                size="small"
                disabled
                class="registered-btn"
            >
              已报名
            </el-button>

            <el-button
                v-else
                :type="(row.isRegistered || row.registered) ? 'success' : 'primary'"
                size="small"
                @click="goToDetail(row.competitionId)"
            >
              {{ (row.isRegistered || row.registered) ? '进入比赛' : '查看详情' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { competitionApi } from '@/api';
import { ElMessage } from 'element-plus';
import { Trophy } from '@element-plus/icons-vue';
import dayjs from 'dayjs';

const router = useRouter();
const competitions = ref([]);
const loading = ref(false);

const fetchCompetitions = async () => {
  loading.value = true;
  try {
    const res = await competitionApi.getList();
    // 兼容拦截器逻辑
    const data = res.code === 200 ? res.data : (res.data?.data || res.data || []);
    competitions.value = Array.isArray(data) ? data : [];
  } catch (err) {
    console.error("列表加载失败:", err);
    ElMessage.error("加载比赛列表失败");
  } finally {
    loading.value = false;
  }
};

const getStatusText = (row) => {
  const now = dayjs();
  if (now.isBefore(dayjs(row.startTime))) return '未开始';
  if (now.isAfter(dayjs(row.endTime))) return '已结束';
  return '进行中';
};

const getStatusType = (row) => {
  const s = getStatusText(row);
  return s === '未开始' ? 'info' : s === '进行中' ? 'success' : 'danger';
};

const getDuration = (start, end) => {
  const diff = dayjs(end).diff(dayjs(start), 'minute');
  const h = Math.floor(diff / 60);
  const m = diff % 60;
  return `${h}小时${m > 0 ? m + '分' : ''}`;
};

const formatTime = (time) => time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-';

const handleRegister = async (id) => {
  try {
    const res = await competitionApi.register(id);
    const result = res.code === 200 ? res : (res.data || res);

    if (result.code === 200) {
      ElMessage.success("报名成功！");
      await fetchCompetitions(); // 报名成功后强制刷新列表状态
    } else {
      ElMessage.warning(result.message || "报名失败");
    }
  } catch (err) {
    const errorBody = err.response?.data;
    const msg = errorBody?.message || "";

    if (msg.includes("已经报名") || msg.includes("过")) {
      ElMessage.info("您已经报名过该比赛");
      fetchCompetitions();
    } else {
      ElMessage.error(msg || "登录后方可报名");
    }
  }
};

const goToDetail = (id) => {
  router.push(`/competition/${id}`);
};

onMounted(fetchCompetitions);
</script>

<style scoped>
.competition-list-container { padding: 20px; background-color: #f8f9fa; min-height: calc(100vh - 60px); }
.box-card { border-radius: 8px; }
.card-header { display: flex; align-items: center; }
.header-left { display: flex; align-items: center; gap: 10px; }
.header-icon { font-size: 24px; color: #e6a23c; }
.header-title { font-size: 20px; font-weight: bold; }
.name-container { display: flex; align-items: center; gap: 8px; }
.reg-badge { font-weight: normal; }
.comp-name-link { font-size: 16px; font-weight: 600; }
.status-tag { width: 80px; text-align: center; }
.time-text, .duration-text { color: #606266; font-size: 14px; }
:deep(.el-table__row) { height: 70px; }

/* 新增：已报名禁用状态样式 */
.registered-btn.is-disabled {
  background-color: #f4f4f5 !important;
  border-color: #e9e9eb !important;
  color: #909399 !important;
  cursor: not-allowed;
}
</style>