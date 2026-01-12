<template>
  <div class="problem-manage">
    <el-card shadow="never">
      <template #header>
        <div class="header-content">
          <span class="title">题目管理</span>
          <el-button type="primary" @click="openAddDialog">新增题目</el-button>
        </div>
      </template>

      <el-table :data="problemList" v-loading="loading" border stripe>
        <el-table-column prop="problemId" label="ID" width="80" align="center" />
        <el-table-column prop="problemName" label="题目标题" show-overflow-tooltip />
        <el-table-column prop="difficulty" label="难度" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getDifficultyType(row.difficulty)">{{ row.difficulty }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="公开状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isPublic ? 'success' : 'info'">
              {{ row.isPublic ? '已公开' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="250" align="center">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handleUploadClick(row)">上传用例</el-button>
            <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dialogVisible" :title="form.problemId ? '编辑题目' : '新增题目'" width="60%">
        <el-form :model="form" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="题目标题"><el-input v-model="form.problemName" /></el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="难度">
                <el-select v-model="form.difficulty">
                  <el-option label="简单" value="简单" />
                  <el-option label="中等" value="中等" />
                  <el-option label="困难" value="困难" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="公开题目">
                <el-switch
                    v-model="form.isPublic"
                    active-text="公开"
                    inactive-text="隐藏"
                    inline-prompt
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="题目描述"><el-input type="textarea" :rows="4" v-model="form.problemDescription" /></el-form-item>
          <el-form-item label="输入描述"><el-input type="textarea" :rows="2" v-model="form.inputDescription" /></el-form-item>
          <el-form-item label="输出描述"><el-input type="textarea" :rows="2" v-model="form.outputDescription" /></el-form-item>
          <el-form-item label="提示">
            <el-input
                type="textarea"
                :rows="2"
                v-model="form.hint"
                placeholder="请输入题目提示信息 (Hint)"
            />
          </el-form-item>
          <el-form-item label="样例管理">
            <div v-for="(sample, index) in form.samples" :key="index" class="sample-item">
              <div class="sample-label">输入样例 {{index + 1}}:</div>
              <el-input
                  v-model="sample.input"
                  type="textarea"
                  :autosize="{ minRows: 2 }"
                  placeholder="请输入输入样例内容"
                  style="margin-bottom: 8px"
              />

              <div class="sample-label">输出样例 {{index + 1}}:</div>
              <el-input
                  v-model="sample.output"
                  type="textarea"
                  :autosize="{ minRows: 2 }"
                  placeholder="请输入输出样例内容"
              />

              <el-button type="danger" icon="Delete" link @click="removeSample(index)" style="margin-top: 5px">
                移除此样例
              </el-button>
            </div>
            <el-button type="dashed" @click="addSample" style="width: 100%">+ 添加一组测试样例</el-button>
          </el-form-item>

          <el-row :gutter="20">
            <el-col :span="12"><el-form-item label="时间限制(ms)"><el-input-number v-number-only v-model="form.timeLimit" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="内存限制(MB)"><el-input-number v-number-only v-model="form.memoryLimit" /></el-form-item></el-col>
          </el-row>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="uploadVisible" title="上传测试数据 (.zip)" width="400px">
        <el-upload
            ref="uploadRef"
            class="upload-demo"
            drag
            action="#"
            :auto-upload="false"
            :on-change="onFileChange"
            limit="1"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">请上传包含 .in 和 .out 文件的 ZIP 压缩包</div>
          </template>
        </el-upload>
        <template #footer>
          <el-button @click="uploadVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="doUpload">开始上传</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'; // 增加了 nextTick
import { adminProblemApi } from '@/api';
import { UploadFilled, Delete } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

const loading = ref(false);
const problemList = ref([]);
const dialogVisible = ref(false);
const uploadVisible = ref(false);
const uploading = ref(false);
const uploadRef = ref(null); // 此处 ref 定义正确

// 初始化 form 对象
const form = ref({
  problemId: null,
  problemName: '',
  problemDescription: '',
  inputDescription: '',
  outputDescription: '',
  hint: '',
  difficulty: '简单',
  timeLimit: 1000,
  memoryLimit: 256,
  isPublic: false,
  samples: [{ input: '', output: '' }]
});

// ... fetchList, addSample 等逻辑保持不变 ...

const openAddDialog = () => {
  form.value = {
    problemName: '',
    problemDescription: '',
    inputDescription: '',
    outputDescription: '',
    hint: '',
    difficulty: '简单',
    timeLimit: 1000,
    memoryLimit: 256,
    isPublic: false,
    samples: []
  };
  dialogVisible.value = true;
};

const currentProblemId = ref(null);
const selectedFile = ref(null);

const fetchList = async () => {
  loading.value = true;
  try {
    const res = await adminProblemApi.list();
    problemList.value = res.data || res;
  } finally { loading.value = false; }
};

const addSample = () => form.value.samples.push({ input: '', output: '' });
const removeSample = (i) => form.value.samples.splice(i, 1);

const submitForm = async () => {
  const res = await adminProblemApi.save(form.value);
  if(res.code === 200) {
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    fetchList();
  }
};

// --- 修改部分开始 ---

const handleUploadClick = (row) => {
  currentProblemId.value = row.problemId;
  selectedFile.value = null;
  uploadVisible.value = true;

  // 核心：在弹窗打开时清空组件内部列表
  nextTick(() => {
    if (uploadRef.value) {
      uploadRef.value.clearFiles();
    }
  });
};

// 监听弹窗关闭，彻底清理
watch(uploadVisible, (val) => {
  if (!val) {
    selectedFile.value = null;
    if (uploadRef.value) {
      uploadRef.value.clearFiles();
    }
  }
});

// --- 修改部分结束 ---

const onFileChange = (file) => {
  selectedFile.value = file.raw;
};

const doUpload = async () => {
  if (!selectedFile.value) return ElMessage.warning("请先选择文件");
  uploading.value = true;
  const formData = new FormData();
  formData.append('file', selectedFile.value);
  formData.append('problemId', currentProblemId.value);

  try {
    const res = await adminProblemApi.uploadTestcase(formData);
    if(res.code === 200) {
      ElMessage.success('用例上传并同步成功');
      uploadVisible.value = false;
    } else { ElMessage.error(res.message); }
  } finally { uploading.value = false; }
};

const getDifficultyType = (d) => {
  if (d === '简单') return 'success';
  if (d === '中等') return 'warning';
  if (d === '困难') return 'danger';
  return 'info';
};

const handleEdit = (row) => {
  form.value = {
    ...row,
    isPublic: !!row.isPublic,
    samples: row.samples ? JSON.parse(JSON.stringify(row.samples)) : []
  };
  dialogVisible.value = true;
};

const handleDelete = (row) => {
  ElMessageBox.confirm(
      `确定要删除题目 [${row.problemName}] 吗？`,
      '警告',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      const res = await adminProblemApi.delete(row.problemId);
      if (res.code === 200) {
        ElMessage.success('删除成功');
        fetchList();
      }
    } catch (error) { ElMessage.error('网络请求失败'); }
  });
};

onMounted(fetchList);
</script>

<style scoped>
.header-content { display: flex; justify-content: space-between; align-items: center; }
.sample-item { border: 1px dashed #ccc; padding: 10px; margin-bottom: 10px; border-radius: 4px; }
.title { font-weight: bold; font-size: 18px; }
.sample-label { font-weight: bold; margin-bottom: 5px; color: #606266; }
</style>