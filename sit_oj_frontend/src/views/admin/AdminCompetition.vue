<template>
  <div class="admin-competition-container">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <div class="header-left">
            <el-icon><Trophy /></el-icon>
            <span class="title">比赛管理控制台</span>
          </div>
          <el-button type="primary" @click="openCreateDialog">发布新比赛</el-button>
        </div>
      </template>

      <el-table :data="competitions" v-loading="loading" style="width: 100%" border stripe>
        <el-table-column prop="competitionId" label="ID" width="80" align="center" />
        <el-table-column prop="competitionName" label="比赛名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="起止时间" width="380" align="center">
          <template #default="scope">
            <el-tag size="small" effect="plain">{{ scope.row.startTime }}</el-tag>
            <span class="time-sep">至</span>
            <el-tag size="small" effect="plain" type="warning">{{ scope.row.endTime }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="380" align="center">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="success" @click="openProblemDialog(scope.row)">关联题目</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row.competitionId)">删除</el-button>
            <el-button
                v-if="scope.row.freezeMinute > 0"
                size="small" type="warning"
                @click="handleUnfreeze(scope.row.competitionId)"
            >解封</el-button>
            <el-button
                size="small" type="info"
                @click="openExportDialog(scope.row)"
            >导出滚榜</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
        v-model="formVisible"
        :title="form.competitionId ? '编辑比赛' : '发布新比赛'"
        width="550px"
        destroy-on-close
    >
      <el-form :model="form" label-width="100px" label-position="left">
        <el-form-item label="比赛名称" required>
          <el-input v-model="form.competitionName" placeholder="例如：2026春季校赛" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
              v-model="form.startTime"
              type="datetime"
              placeholder="选择开始时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
              :disabled="form.competitionId && isStarted(form.startTime)"
          />
          <div v-if="form.competitionId && isStarted(form.startTime)" class="freeze-tip">比赛已开始，不可修改时间</div>
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
              v-model="form.endTime"
              type="datetime"
              placeholder="选择结束时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
              :disabled="form.competitionId && isStarted(form.startTime)"
          />
          <div v-if="!form.competitionId" class="freeze-tip">结束时间必须大于开始时间</div>
        </el-form-item>
        <el-form-item v-if="!form.competitionId" label="封榜时长">
          <el-input-number v-model="form.freezeMinute" :min="0" :max="300" style="width: 100%" />
          <div class="freeze-tip">比赛结束前 N 分钟冻结排行榜，0 = 不封榜（创建后不可修改）</div>
        </el-form-item>
        <el-form-item v-else label="封榜时长">
          <el-input :value="form.freezeMinute + ' 分钟（不可修改）'" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="problemDialogVisible" title="关联题目到比赛" width="800px">
      <div class="dialog-tip">请选择要加入本场比赛的题目（支持多选，已自动勾选已有题目）</div>
      <el-table
          :data="allProblems"
          v-loading="loadingProblems"
          @selection-change="handleSelectionChange"
          max-height="400"
          border
          ref="problemTableRef"
          row-key="problemId"
      >
        <el-table-column type="selection" width="55" align="center" :reserve-selection="true" />
        <el-table-column prop="problemId" label="ID" width="80" align="center" />
        <el-table-column prop="problemName" label="题目名称" />
        <el-table-column prop="difficulty" label="难度" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.difficulty }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="problemDialogVisible = false">取消</el-button>
        <el-button
            type="primary"
            :loading="linking"
            @click="confirmAddProblems"
        >
          确认关联 (已选 {{ selectedProblemIds.length }} 题)
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
        v-model="exportDialogVisible"
        :title="`导出滚榜：${exportCompetition?.competitionName || ''}`"
        width="800px"
        :close-on-click-modal="!exporting"
        :close-on-press-escape="!exporting"
        :show-close="!exporting"
    >
      <div class="dialog-tip">
        participants：线下正式参赛，参与奖牌分配；stars：线上非正式参赛，不参与奖牌分配。
        本次选择仅用于导出。金、银、铜牌名额按 participants 人数的 10%、20%、30% 计算。
        奖牌和分组冠军按包含封榜期间提交的最终成绩计算；stars 可获一血和本组冠军。
      </div>
      <div class="export-toolbar">
        <el-input v-model="exportSearch" placeholder="搜索用户 ID、用户名或姓名" clearable :disabled="exporting" />
        <el-button :disabled="exporting || loadingExportParticipants || !!exportLoadError || !exportParticipants.length" @click="toggleAllExportParticipants">
          {{ selectedExportCount === exportParticipants.length ? '取消全选' : '全选' }}
        </el-button>
        <span>已选 {{ selectedExportCount }} 人</span>
        <span>participants {{ participantCount }} 人 / stars {{ starCount }} 人</span>
      </div>
      <el-alert v-if="exportLoadError" :title="exportLoadError" type="error" :closable="false" />
      <el-table :data="filteredExportParticipants" row-key="userId" v-loading="loadingExportParticipants" border max-height="400">
        <el-table-column label="导出" width="65" align="center">
          <template #default="{ row }">
            <el-checkbox v-model="row.selected" :disabled="exporting" :aria-label="`导出选手 ${row.username || row.userId}`" />
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户 ID" width="90" />
        <el-table-column prop="username" label="用户名" min-width="130" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column label="导出状态" width="300">
          <template #default="{ row }">
            <el-select v-model="row.group" :disabled="exporting" style="width: 100%">
              <el-option label="participants（线下正式参赛）" value="participants" />
              <el-option label="stars（线上非正式参赛）" value="stars" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button :disabled="exporting" @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exporting" :disabled="loadingExportParticipants || !!exportLoadError || !selectedExportCount" @click="handleExport">
          确认导出
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Trophy } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
// 引入你提供的 API
import { competitionApi, adminCompetitionApi, problemApi } from '@/api';

const loading = ref(false);
const submitting = ref(false);
const linking = ref(false);
const competitions = ref([]);

const formVisible = ref(false);
const form = ref({
  competitionId: null,
  competitionName: '',
  startTime: '',
  endTime: '',
  freezeMinute: 0
});

const problemDialogVisible = ref(false);
const loadingProblems = ref(false);
const allProblems = ref([]);
const selectedProblemIds = ref([]);
const currentCompId = ref(null);
const problemTableRef = ref(null);

const exportDialogVisible = ref(false);
const exportCompetition = ref(null);
const exportParticipants = ref([]);
const loadingExportParticipants = ref(false);
const exportLoadError = ref('');
const exporting = ref(false);
const exportSearch = ref('');
let exportLoadVersion = 0;
const selectedExportParticipants = computed(() => exportParticipants.value.filter(user => user.selected));
const selectedExportCount = computed(() => selectedExportParticipants.value.length);
const starCount = computed(() => selectedExportParticipants.value.filter(user => user.group === 'stars').length);
const participantCount = computed(() => selectedExportCount.value - starCount.value);
const filteredExportParticipants = computed(() => {
  const search = exportSearch.value.trim().toLowerCase();
  return exportParticipants.value.filter(user => [user.userId, user.username, user.realName]
      .some(value => String(value ?? '').toLowerCase().includes(search)));
});
const toggleAllExportParticipants = () => {
  const selected = selectedExportCount.value !== exportParticipants.value.length;
  exportParticipants.value.forEach(user => { user.selected = selected; });
};

// 1. 获取比赛列表
const loadCompetitions = async () => {
  loading.value = true;
  try {
    const res = await competitionApi.getList({ current: 1, size: 100 });
    // 分页响应: { code: 200, data: { records: [...], total: N } }
    competitions.value = res.data?.records || [];
  } catch (error) {
    ElMessage.error("获取比赛列表失败");
  } finally {
    loading.value = false;
  }
};

// 2. 比赛基本信息操作
const openCreateDialog = () => {
  form.value = { competitionId: null, competitionName: '', startTime: '', endTime: '', freezeMinute: 0 };
  formVisible.value = true;
};

const handleEdit = (row) => {
  form.value = { ...row };
  formVisible.value = true;
};

const isStarted = (startTime) => {
  return startTime && dayjs().isAfter(dayjs(startTime));
};

const submitForm = async () => {
  if (!form.value.competitionName || !form.value.startTime || !form.value.endTime) {
    return ElMessage.warning("请填写完整信息");
  }
  if (!form.value.competitionId) {
    if (!dayjs(form.value.endTime).isAfter(dayjs(form.value.startTime))) {
      return ElMessage.warning("结束时间必须大于开始时间");
    }
  }
  submitting.value = true;
  try {
    let res;
    if (form.value.competitionId) {
      res = await adminCompetitionApi.update(form.value);
    } else {
      res = await adminCompetitionApi.create(form.value);
    }
    if (res.code === 200) {
      ElMessage.success('保存成功');
      formVisible.value = false;
      loadCompetitions();
    }
  } finally {
    submitting.value = false;
  }
};

const handleDelete = (id) => {
  ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }).then(async () => {
    const res = await adminCompetitionApi.delete(id);
    if (res.code === 200) {
      ElMessage.success('删除成功');
      loadCompetitions();
    }
  });
};

// 3. 【核心修改】关联题目逻辑
const openProblemDialog = async (row) => {
  currentCompId.value = row.competitionId;
  problemDialogVisible.value = true;
  loadingProblems.value = true;

  try {
    // A. 并行请求：所有题目库 + 该比赛的准确详情（含已关联题目）
    const [allProbRes, detailRes] = await Promise.all([
      problemApi.getList({ current: 1, size: 500 }),
      competitionApi.getById(row.competitionId)
    ]);

    // 分页响应: { code: 200, data: { records: [...], total: N } }
    allProblems.value = allProbRes.data?.records || [];
    const detail = detailRes.data || detailRes;

    // B. 提取已绑定的 ID 列表
    const bindedIds = (detail.problems || []).map(p => p.problemId);

    // C. 确保 DOM 加载后打勾
    await nextTick();
    if (problemTableRef.value) {
      problemTableRef.value.clearSelection();
      allProblems.value.forEach(prob => {
        if (bindedIds.includes(prob.problemId)) {
          // 这里的第二个参数 true 表示选中
          problemTableRef.value.toggleRowSelection(prob, true);
        }
      });
    }
  } catch (error) {
    ElMessage.error("加载关联数据失败");
  } finally {
    loadingProblems.value = false;
  }
};

const handleSelectionChange = (selection) => {
  selectedProblemIds.value = selection.map(item => item.problemId);
};

const confirmAddProblems = async () => {
  linking.value = true;
  try {
    const res = await adminCompetitionApi.addProblems({
      competitionId: currentCompId.value,
      problemIds: selectedProblemIds.value
    });
    if (res.code === 200) {
      ElMessage.success('关联成功');
      problemDialogVisible.value = false;
      loadCompetitions(); // 刷新主表数据
    }
  } finally {
    linking.value = false;
  }
};

const handleUnfreeze = (id) => {
  ElMessageBox.confirm('解封后将公开封榜期间的排名数据，确定解封？', '确认解封', {
    confirmButtonText: '确定解封',
    type: 'warning'
  }).then(async () => {
    const res = await adminCompetitionApi.unfreeze(id);
    if (res.code === 200) {
      ElMessage.success('已解封');
      loadCompetitions();
    } else {
      ElMessage.error(res.message || '解封失败');
    }
  }).catch(() => {});
};

const openExportDialog = async (row) => {
  const version = ++exportLoadVersion;
  exportCompetition.value = row;
  exportParticipants.value = [];
  exportSearch.value = '';
  exportLoadError.value = '';
  exportDialogVisible.value = true;
  loadingExportParticipants.value = true;
  try {
    const res = await adminCompetitionApi.getResolverParticipants(row.competitionId);
    if (version !== exportLoadVersion) return;
    if (res.code !== 200 || !Array.isArray(res.data)) {
      throw new Error(res.message || '加载导出名单失败，请关闭窗口后重试');
    }
    exportParticipants.value = res.data.map(user => ({ ...user, group: 'participants', selected: true }));
  } catch (e) {
    if (version === exportLoadVersion) {
      exportLoadError.value = e.message || '加载导出名单失败，请关闭窗口后重试';
    }
  } finally {
    if (version === exportLoadVersion) loadingExportParticipants.value = false;
  }
};

const handleExport = async () => {
  if (!exportCompetition.value || loadingExportParticipants.value || exportLoadError.value || exporting.value || !selectedExportCount.value) return;
  const competitionId = exportCompetition.value.competitionId;
  const selectedUserIds = selectedExportParticipants.value.map(user => user.userId);
  const userGroups = Object.fromEntries(selectedExportParticipants.value.map(user => [user.userId, user.group]));
  exporting.value = true;
  try {
    const res = await adminCompetitionApi.exportResolver(competitionId, userGroups, selectedUserIds);
    if (res.code === 200 && res.data) {
      const blob = new Blob([res.data], { type: 'application/x-ndjson' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `contest_${competitionId}_event-feed.ndjson`;
      a.click();
      URL.revokeObjectURL(url);
      exportDialogVisible.value = false;
      ElMessage.success('导出成功，可用于 ICPC Resolver 工具');
    } else {
      ElMessage.error(res.message || '导出失败');
    }
  } catch (e) {
    ElMessage.error('导出失败');
  } finally {
    exporting.value = false;
  }
};

onMounted(loadCompetitions);
</script>

<style scoped>
.export-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.export-toolbar .el-input {
  flex: 1;
}

.export-toolbar span {
  flex-shrink: 0;
  color: #606266;
  font-size: 13px;
}

.admin-competition-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
}

.time-sep {
  margin: 0 10px;
  color: #909399;
}

.dialog-tip {
  margin-bottom: 15px;
  color: #606266;
  font-size: 14px;
  background: #fdf6ec;
  padding: 10px;
  border-left: 4px solid #e6a23c;
}
.freeze-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
