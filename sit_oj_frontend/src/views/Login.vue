<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-header">
        <img src="/favicon.ico" class="logo" />
        <h2>SIT-OJ 登录</h2>
      </div>

      <el-form :model="loginForm" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>

        <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>

        <div class="form-footer">
          <el-link type="info" @click="forgotDialogVisible = true" style="margin-right: 15px;">忘记密码？</el-link>
          <span>没有账号？<el-link type="primary" @click="$router.push('/register')">立即注册</el-link></span>
        </div>
      </el-form>
    </el-card>

    <el-dialog v-model="forgotDialogVisible" title="重置密码" width="400px" destroy-on-close>
      <el-form :model="forgotForm" label-position="top">
        <el-form-item label="注册邮箱">
          <el-input v-model="forgotForm.email" placeholder="请输入您的注册邮箱">
            <template #append>
              <el-button @click="handleSendForgotCode" :disabled="countdown > 0">
                {{ countdown > 0 ? `${countdown}s` : '获取' }}
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="forgotForm.code" placeholder="请输入 6 位验证码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="forgotForm.password" type="password" show-password placeholder="至少 6 位新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="forgotDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleResetPassword" :loading="resetLoading">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { userApi } from '@/api';
import { ElMessage } from 'element-plus';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();
const loading = ref(false);
const loginForm = ref({ username: '', password: '' });

// 忘记密码相关变量
const forgotDialogVisible = ref(false);
const resetLoading = ref(false);
const countdown = ref(0);
const forgotForm = ref({ email: '', code: '', password: '' });

const handleLogin = async () => {
  loading.value = true;
  try {
    const res = await userApi.login(loginForm.value);
    if (res.code === 200) {
      localStorage.setItem('token', res.data);
      ElMessage.success("登录成功");
      router.push('/');
    } else {
      ElMessage.error(res.message || "登录异常");
      localStorage.removeItem('token');
    }
  } catch (err) {
    const msg = err.response?.data?.message || err.message || "系统繁忙";
    ElMessage.error("请求失败：" + msg);
  } finally {
    loading.value = false;
  }
};

// 发送重置密码验证码
// 修改后的发送重置验证码逻辑
const handleSendForgotCode = async () => {
  if (!forgotForm.value.email) return ElMessage.warning("请先输入邮箱");

  // 1. 【核心优化】先启动倒计时，消除卡顿感
  countdown.value = 60;
  const timer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      clearInterval(timer);
    }
  }, 1000);

  try {
    // 2. 后台异步请求发送验证码
    // 注意：这里的 type 最好对应后端的逻辑，比如 'update' 或 'reset'
    await userApi.sendCode(forgotForm.value.email, 'update');
    ElMessage.success("验证码已发送，请检查邮箱");
  } catch (err) {
    // 3. 如果请求失败，立即恢复状态
    clearInterval(timer);
    countdown.value = 0;
    ElMessage.error(err.response?.data?.message || "发送失败，请检查邮箱是否正确");
  }
};

// 执行重置密码
const handleResetPassword = async () => {
  if (!forgotForm.value.code || !forgotForm.value.password) {
    return ElMessage.error("请填写完整信息");
  }
  resetLoading.value = true;
  try {
    // 后端 updateUserInfo 接收 User 对象。
    // 忘记密码时，我们传 email 而不是 ID，后端需要根据 email 找到用户
    const res = await userApi.resetPassword(forgotForm.value);
    if (res.code === 200) {
      ElMessage.success("密码重置成功，请登录");
      forgotDialogVisible.value = false;
    } else {
      ElMessage.error(res.message);
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || "重置失败");
  } finally {
    resetLoading.value = false;
  }
};
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #f0f2f5;
}
.login-card {
  width: 380px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.login-header {
  text-align: center;
  margin-bottom: 30px;
}
.logo {
  width: 48px;
  margin-bottom: 10px;
}
.login-btn {
  width: 100%;
  height: 40px;
  font-size: 16px;
}
.form-footer {
  margin-top: 15px;
  text-align: center;
  font-size: 14px;
}
</style>