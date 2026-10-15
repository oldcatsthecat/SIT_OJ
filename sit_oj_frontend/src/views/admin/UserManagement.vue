<template>
  <div class="user-management">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">用户管理系统</span>
          <el-button type="primary" @click="() => fetchUsers(currentPage)">刷新数据</el-button>
        </div>
      </template>

      <el-table :data="users" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="真实姓名" width="100" />
        <el-table-column prop="studentId" label="学号" width="130" />

        <el-table-column prop="gender" label="性别" width="70" align="center" />
        <el-table-column prop="age" label="年龄" width="70" align="center" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />

        <el-table-column prop="role" label="权限" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">{{ row.role }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.locked ? 'danger' : 'success'">{{ row.locked ? '已禁用' : '正常' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openEditDialog(row)">编辑</el-button>

            <el-button
                size="small"
                :type="row.role === 'ADMIN' ? 'warning' : 'success'"
                :disabled="isSelf(row.id) || row.username === 'root'"
                @click="handleRoleChange(row)"
            >
              权限
            </el-button>

            <el-popconfirm :title="row.locked ? '确定启用此账户？' : '确定禁用此账户？'" @confirm="handleLockChange(row)">
              <template #reference>
                <el-button size="small" :type="row.locked ? 'success' : 'warning'"
                           :disabled="row.username === 'root' || (isSelf(row.id) && !row.locked)">
                  {{ row.locked ? '启用' : '禁用' }}
                </el-button>
              </template>
            </el-popconfirm>

            <el-popconfirm title="确定删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger" :disabled="row.username === 'root' || isSelf(row.id)">删除</el-button>
              </template>
            </el-popconfirm>
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

    <el-dialog v-model="editVisible" title="修改用户信息" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="editForm.realName" placeholder="最多50个字符" />
        </el-form-item>
        <el-form-item label="学号">
          <el-input v-model="editForm.studentId" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="editForm.gender">
            <el-radio label="男">男</el-radio>
            <el-radio label="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="editForm.age" :min="1" :max="120" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUpdate" :loading="submitting">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import { adminUserApi } from '@/api';
import { isValidRealName } from '@/utils/realName';

const users = ref([]);
const loading = ref(false);
const editVisible = ref(false);
const submitting = ref(false);
const editForm = reactive({});

// 分页
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);

// 判断是否为当前登录用户（从 JWT token 解析）
const isSelf = (targetId) => {
  try {
    const token = localStorage.getItem('token');
    if (!token) return false;
    const payload = JSON.parse(atob(token.split('.')[1]));
    return (payload.id || payload.userId) === targetId;
  } catch { return false; }
};

const fetchUsers = async (page = 1) => {
  loading.value = true;
  try {
    const res = await adminUserApi.listAll({ current: page, size: pageSize.value });
    if (res && res.code === 200 && res.data) {
      users.value = res.data.records || [];
      total.value = res.data.total || 0;
    }
  } catch (err) { ElMessage.error('加载失败'); }
  finally { loading.value = false; }
};

const handlePageChange = (page) => {
  currentPage.value = page;
  fetchUsers(page);
};

const openEditDialog = (row) => {
  Object.assign(editForm, row);
  editVisible.value = true;
};

const submitUpdate = async () => {
  if (editForm.realName != null && !isValidRealName(editForm.realName)) {
    ElMessage.warning('姓名不能为空且不能超过50个字符');
    return;
  }
  submitting.value = true;
  try {
    const res = await adminUserApi.update(editForm);
    if (res.code === 200) {
      ElMessage.success('更新成功');
      editVisible.value = false;
      fetchUsers(currentPage.value);
    }
  } catch (err) { ElMessage.error('更新失败'); }
  finally { submitting.value = false; }
};

const handleRoleChange = async (row) => {
  // 逻辑拦截：如果是自己，拦截操作
  if (isSelf(row.id)) {
    ElMessage.warning('为了系统安全，您不能修改自己的权限');
    return;
  }

  const newRole = row.role === 'ADMIN' ? 'USER' : 'ADMIN';
  const res = await adminUserApi.update({ ...row, role: newRole });
  if (res.code === 200) {
    ElMessage.success(`用户 ${row.username} 权限已切换为 ${newRole}`);
    fetchUsers(currentPage.value);
  }
};

const handleDelete = async (id) => {
  // 逻辑拦截：不能删除自己
  if (isSelf(id)) {
    ElMessage.warning('不能删除当前登录的账号');
    return;
  }
  const res = await adminUserApi.delete(id);
  if (res.code === 200) {
    ElMessage.success('已删除');
    fetchUsers(currentPage.value);
  }
};

const handleLockChange = async (row) => {
  try {
    const res = await adminUserApi.setLocked(row.id, !row.locked);
    if (res.code === 200) {
      ElMessage.success(row.locked ? '账户已启用' : '账户已禁用');
      await fetchUsers(currentPage.value);
    } else {
      ElMessage.error(res.message || '操作失败');
    }
  } catch {
    ElMessage.error('操作失败');
  }
};

onMounted(fetchUsers);
</script>

<style scoped>
.user-management {
  padding: 10px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title {
  font-size: 18px;
  font-weight: bold;
}
.pagination-wrapper { margin-top: 30px; display: flex; justify-content: center; }
</style>
