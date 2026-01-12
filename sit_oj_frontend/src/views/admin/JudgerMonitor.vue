<template>
  <div class="admin-judger-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>测评机状态 ({{ status.service_url || '未知节点' }})</span>
          <el-button type="primary" size="small" @click="fetchStatus" :loading="refreshing">手动刷新</el-button>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="主机名">{{ status.hostname || 'N/A' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ status.judger_version || 'N/A' }}</el-descriptions-item>
        <el-descriptions-item label="CPU 核心">{{ status.cpu_core || 0 }} 核</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="isOnline ? 'success' : 'danger'" effect="dark">
            {{ isOnline ? '运行中' : '离线' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="monitor-charts">
        <el-progress type="dashboard" :percentage="Number(status.cpu || 0)" :color="customColors">
          <template #default="{ percentage }">
            <div class="percentage-value">{{ percentage }}%</div>
            <div class="percentage-label">CPU</div>
          </template>
        </el-progress>

        <el-progress type="dashboard" :percentage="Number(status.memory || 0)" color="#67C23A">
          <template #default="{ percentage }">
            <div class="percentage-value">{{ percentage }}%</div>
            <div class="percentage-label">内存</div>
          </template>
        </el-progress>
      </div>

      <div v-if="!isOnline" class="offline-tip">
        <el-alert title="判题服务可能已断开连接，请检查后端 Judger 进程" type="error" show-icon :closable="false" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { adminJudgeApi } from '@/api' // 引入你刚才写的接口

const status = ref({})
const refreshing = ref(false)
let timer = null

const isOnline = computed(() => {
  if (!status.value.last_seen) return false
  // 15秒心跳机制
  return (Date.now() - status.value.last_seen) < 15000
})

const customColors = [
  { color: '#409eff', percentage: 20 },
  { color: '#e6a23c', percentage: 60 },
  { color: '#f56c6c', percentage: 80 },
]

const fetchStatus = async () => {
  try {
    refreshing.value = true
    const res = await adminJudgeApi.getServerStatus()
    // 假设后端返回 Result.success(data) 结构，根据你实际 api.js 的拦截器调整
    status.value = res.data || res
  } catch (err) {
    console.error("获取测评机状态失败", err)
  } finally {
    refreshing.value = false
  }
}

onMounted(() => {
  fetchStatus()
  timer = setInterval(fetchStatus, 5000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.monitor-charts {
  margin-top: 30px;
  display: flex;
  gap: 60px;
  justify-content: center;
}
.percentage-value { font-size: 24px; font-weight: bold; }
.percentage-label { font-size: 12px; color: #909399; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.offline-tip { margin-top: 20px; }
</style>