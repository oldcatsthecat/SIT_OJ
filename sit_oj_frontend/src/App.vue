<template>
  <div class="app-wrapper">
    <el-header class="nav-header">
      <div class="nav-content">
        <div class="logo" @click="$router.push('/')">
          <img src="/favicon.ico" class="logo-img" />
          <span class="logo-text">SIT-OJ</span>
        </div>

        <div class="menu">
          <el-button text @click="$router.push('/problems')">题目列表</el-button>
          <el-button text @click="$router.push('/submissions')">提交列表</el-button>
          <el-button text @click="$router.push('/competitions')">比赛列表</el-button>
          <el-button text @click="$router.push('/about')">关于</el-button>
        </div>

        <div class="user-section">
          <template v-if="!isLoggedIn">
            <el-button type="primary" plain size="small" @click="$router.push('/login')">登录</el-button>
            <el-button type="primary" size="small" @click="$router.push('/register')">注册</el-button>
          </template>

          <el-dropdown v-else @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="30" icon="UserFilled" />
              <span class="username">{{ username }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="userRole === 'ADMIN'" command="admin" icon="Setting">
                  后台管理
                </el-dropdown-item>

                <el-dropdown-item command="profile" icon="User">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout" icon="SwitchButton">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </el-header>

    <el-main class="main-container">
      <router-view />
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getTokenInfo, isTokenExpired, refreshToken, removeToken } from '@/utils/auth';

const route = useRoute();
const router = useRouter();
const isLoggedIn = ref(false);
const username = ref('');
const userRole = ref('');

const checkUserStatus = async () => {
  const tokenInfo = getTokenInfo();

  if (!tokenInfo) {
    // 无 token 或解析失败
    isLoggedIn.value = false;
    userRole.value = '';
    username.value = '';
    return;
  }

  // 检查是否过期
  if (isTokenExpired()) {
    // 尝试刷新 token
    const newToken = await refreshToken();
    if (newToken) {
      // 刷新成功，重新读取用户信息
      const newInfo = getTokenInfo();
      if (newInfo) {
        username.value = newInfo.username;
        userRole.value = newInfo.role;
        isLoggedIn.value = true;
        return;
      }
    }
    // 刷新失败，清除登录状态
    removeToken();
    isLoggedIn.value = false;
    userRole.value = '';
    username.value = '';
    return;
  }

  // Token 有效
  username.value = tokenInfo.username;
  userRole.value = tokenInfo.role;
  isLoggedIn.value = true;
};


// App.vue 建议优化
watch(() => route.path, (newPath, oldPath) => {
  // 只有当路径真正变化，或者从登录页出来时才检查
  if (newPath !== oldPath) {
    checkUserStatus();
  }
}, { immediate: true });

const handleCommand = (command) => {
  switch (command) {
    case 'admin' :
      router.push('/admin/users');
      break;
    case 'logout':
      removeToken();
      isLoggedIn.value = false;
      router.push('/login');
      break;
    case 'profile':
      router.push('/profile');
      break;
  }
};
</script>


<style scoped>
.app-wrapper {
  min-height: 100vh;
  background-color: #f5f7fa;
}
.nav-header {
  background-color: #fff;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
  display: flex;
  justify-content: center;
}
.nav-content {
  width: 1200px;
  display: flex;
  align-items: center;
  height: 60px;
}
.logo {
  display: flex;
  align-items: center;
  cursor: pointer;
  margin-right: 40px;
}
.logo-img { width: 32px; margin-right: 10px; }
.logo-text { font-size: 22px; font-weight: bold; color: #409eff; }
.menu { flex: 1; }
.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  gap: 10px;
}
.username { font-size: 14px; color: #606266; }
.main-container {
  padding: 20px;
  width: 1200px;
  margin: 0 auto;
}
</style>