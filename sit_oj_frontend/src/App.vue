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
import { jwtDecode } from 'jwt-decode'; // 必须带大括号

const route = useRoute();
const router = useRouter();
const isLoggedIn = ref(false);
const username = ref('');

const userRole = ref('');

const checkUserStatus = () => {
  const token = localStorage.getItem('token');
  if (token && token.startsWith('eyJ')) {
    try {
      const decoded = jwtDecode(token);
      username.value = decoded.sub || decoded.username;
      // 1. 从 Token 中获取角色
      userRole.value = decoded.role || '';
      isLoggedIn.value = true;
    } catch (e) {
      isLoggedIn.value = false;
    }
  } else {
    isLoggedIn.value = false;
    userRole.value = '';
  }
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
      localStorage.removeItem('token');
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