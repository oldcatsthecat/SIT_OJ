<template>
  <div class="auth-container">
    <el-card class="auth-card">
      <h2 class="title">加入 SIT-OJ</h2>
      <el-form :model="regForm" :rules="rules" ref="regFormRef" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="regForm.username" placeholder="建议使用学号或英文名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="regForm.password" type="password" show-password placeholder="至少 6 位字符" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="regForm.email" placeholder="请输入常用邮箱">
            <template #append>
              <el-button @click="handleSendCode" :disabled="countdown > 0">
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <el-input v-model="regForm.code" placeholder="请输入 6 位邮箱验证码" />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="regForm.realName" placeholder="用于颁发证书" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="学号" prop="studentId">
              <el-input v-model="regForm.studentId" placeholder="请输入学号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-select v-model="regForm.gender" style="width: 100%">
                <el-option label="男" value="男" />
                <el-option label="女" value="女" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="年龄" prop="age">
              <el-input-number v-model="regForm.age" :min="10" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-button type="primary" class="full-btn" @click="handleRegister" :loading="loading">
          立即注册
        </el-button>
        <div class="footer-link">
          已有账号？<router-link to="/login">立即登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { userApi } from '@/api';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';

const router = useRouter();
const regFormRef = ref(null);
const loading = ref(false);
const countdown = ref(0);

const regForm = ref({
  username: '',
  password: '',
  email: '', // 新增
  code: '',  // 新增
  realName: '',
  studentId: '',
  gender: '男',
  age: 18
});

const rules = {
  username: [{required: true, message: '用户名不能为空', trigger: 'blur'}],
  password: [{required: true, min: 6, message: '密码不能少于6位', trigger: 'blur'}],
  email: [
    {required: true, message: '邮箱不能为空', trigger: 'blur'},
    {type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur'}
  ],
  code: [{required: true, message: '请输入验证码', trigger: 'blur'}],
  realName: [{required: true, message: '请输入真实姓名', trigger: 'blur'}],
  studentId: [{required: true, message: '学号不能为空', trigger: 'blur'}]
};

// 发送验证码逻辑
// 发送验证码逻辑
const handleSendCode = async () => {
  // 1. 前置校验
  if (!regForm.value.email) return ElMessage.warning("请先输入邮箱地址");
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailPattern.test(regForm.value.email)) return ElMessage.error("邮箱格式不正确");

  // 2. 【核心优化】先开启倒计时，让用户感觉“秒回”
  countdown.value = 60;
  const timer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) clearInterval(timer);
  }, 1000);

  try {
    // 3. 后台发送请求
    await userApi.sendCode(regForm.value.email, 'register');
    ElMessage.success("验证码已发送，请查收邮件");
  } catch (err) {
    // 4. 如果发送失败，清除倒计时并归零，允许重试
    clearInterval(timer);
    countdown.value = 0;
    ElMessage.error(err.response?.data?.message || "发送验证码失败");
  }
};

const handleRegister = async () => {
  if (!regFormRef.value) return;

  await regFormRef.value.validate(async (valid) => {
    if (!valid) return;
    loading.value = true;
    try {
      // 发送完整的 regForm 对象，包含后端需要的所有字段
      await userApi.register(regForm.value);
      ElMessage.success("注册成功！请登录");
      router.push('/login');
    } catch (err) {
      ElMessage.error(err.response?.data?.message || err.message || "注册失败");
    } finally {
      loading.value = false;
    }
  });
};
</script>

<style scoped>
/* 样式完全保持不变 */
.auth-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}
.auth-card {
  width: 450px;
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
}
.title {
  text-align: center;
  color: #409eff;
  margin-bottom: 30px;
  font-weight: 600;
}
.full-btn {
  width: 100%;
  margin-top: 20px;
  height: 40px;
  font-size: 16px;
}
.footer-link {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: #666;
}
</style>