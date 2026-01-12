<template>
  <div class="user-management">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">用户管理</span>
          <el-button type="primary" @click="fetchUsers">刷新数据</el-button>
        </div>
      </template>

      <el-table :data="users" v-loading="loading" style="width: 100%" border stripe>
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="realName" label="真实姓名" width="120" />
        <el-table-column prop="studentId" label="学号" width="150" />
        <el-table-column prop="gender" label="性别" width="80" align="center" />

        <el-table-column prop="role" label="权限角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" effect="dark">
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="注册时间" width="180">
          <template #default="{ row }">
            {{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="200" align="center">
          <template #default="{ row }">
            <el-button
                size="small"
                :type="row.role === 'ADMIN' ? 'warning' : 'success'"
                @click="handleRoleChange(row)"
            >
              {{ row.role === 'ADMIN' ? '降级为用户' : '提升管理员' }}
            </el-button>

            <el-popconfirm
                title="确定要删除该用户吗？此操作不可逆，且会删除该用户的所有提交记录！"
                confirm-button-text="确定"
                cancel-button-text="取消"
                @confirm="handleDelete(row.id)"
            >
              <template #reference>
                <el-button size="small" type="danger" :disabled="row.username === 'root'">
                  删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { adminUserApi } from '@/api'; // 确保你在 api/index.js 导出了这个

const users = ref([]);
const loading = ref(false);

// 获取用户列表
const fetchUsers = async () => {
  loading.value = true;
  try {
    const res = await adminUserApi.listAll();
    // 假设后端返回 Result<List<User>>，res 为 {code, data, message}
    if (res.code === 200) {
      users.value = res.data;
    } else {
      ElMessage.error(res.message || '获取列表失败');
    }
  } catch (error) {
    ElMessage.error('网络错误或权限不足');
  } finally {
    loading.value = false;
  }
};

// 切换角色
const handleRoleChange = async (row) => {
  const newRole = row.role === 'ADMIN' ? 'USER' : 'ADMIN';
  try {
    const res = await adminUserApi.update({ ...row, role: newRole });
    if (res.code === 200) {
      ElMessage.success(`已将用户 ${row.username} 设置为 ${newRole}`);
      fetchUsers(); // 刷新列表
    }
  } catch (error) {
    ElMessage.error('操作失败');
  }
};

// 删除用户
const handleDelete = async (id) => {
  try {
    const res = await adminUserApi.delete(id);
    if (res.code === 200) {
      ElMessage.success('用户及其关联数据已成功删除');
      fetchUsers();
    }
  } catch (error) {
    ElMessage.error('删除失败');
  }
};

onMounted(() => {
  fetchUsers();
});
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
</style>