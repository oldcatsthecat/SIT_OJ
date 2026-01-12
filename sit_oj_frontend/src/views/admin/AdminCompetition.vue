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
        <el-table-column label="操作" width="280" align="center">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="success" @click="openProblemDialog(scope.row)">关联题目</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row.competitionId)">删除</el-button>
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
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
              v-model="form.endTime"
              type="datetime"
              placeholder="选择结束时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
          />
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
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Trophy } from '@element-plus/icons-vue';
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
  endTime: ''
});

const problemDialogVisible = ref(false);
const loadingProblems = ref(false);
const allProblems = ref([]);
const selectedProblemIds = ref([]);
const currentCompId = ref(null);
const problemTableRef = ref(null);

// 1. 获取比赛列表
const loadCompetitions = async () => {
  loading.value = true;
  try {
    const res = await competitionApi.getList();
    competitions.value = res.data || res;
  } catch (error) {
    ElMessage.error("获取比赛列表失败");
  } finally {
    loading.value = false;
  }
};

// 2. 比赛基本信息操作
const openCreateDialog = () => {
  form.value = { competitionId: null, competitionName: '', startTime: '', endTime: '' };
  formVisible.value = true;
};

const handleEdit = (row) => {
  form.value = { ...row };
  formVisible.value = true;
};

const submitForm = async () => {
  if (!form.value.competitionName || !form.value.startTime || !form.value.endTime) {
    return ElMessage.warning("请填写完整信息");
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
      problemApi.getList(),
      competitionApi.getById(row.competitionId) // 调用 /{id} 获取完整 problems 列表
    ]);

    allProblems.value = allProbRes.data || allProbRes;
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

onMounted(loadCompetitions);
</script>

<style scoped>
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
</style>