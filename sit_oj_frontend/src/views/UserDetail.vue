<template>
  <div class="user-detail-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
          <span style="font-weight: bold; font-size: 18px;">个人档案</span>
          <div class="action-btns">
            <el-button type="warning" @click="openEmailDialog">修改邮箱</el-button>
            <el-button type="danger" @click="openPwdDialog">修改密码</el-button>
            <el-button :type="isEditing ? 'info' : 'primary'" @click="toggleEdit">
              {{ isEditing ? '取消修改' : '编辑资料' }}
            </el-button>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户名">{{ userInfo.username }}</el-descriptions-item>

        <el-descriptions-item label="真实姓名">
          <el-input v-if="isEditing" v-model="editForm.realName" placeholder="请输入姓名" />
          <span v-else>{{ userInfo.realName || '未填写' }}</span>
        </el-descriptions-item>

        <el-descriptions-item label="学号">
          <el-input v-if="isEditing" v-model="editForm.studentId" placeholder="请输入学号" />
          <span v-else>{{ userInfo.studentId || '未填写' }}</span>
        </el-descriptions-item>

        <el-descriptions-item label="性别">
          <el-select v-if="isEditing" v-model="editForm.gender" placeholder="请选择" style="width: 100%">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
          <span v-else>{{ userInfo.gender || '未填写'}}</span>
        </el-descriptions-item>

        <el-descriptions-item label="角色">
          <el-tag :type="userInfo.role === 'ADMIN' ? 'danger' : 'success'">
            {{ userInfo.role }}
          </el-tag>
        </el-descriptions-item>

        <el-descriptions-item label="邮箱">
          <span>{{ userInfo.email || '未绑定' }}</span>
        </el-descriptions-item>

        <el-descriptions-item label="年龄">
          <el-input-number v-if="isEditing" v-model="editForm.age" :min="1" :max="120" />
          <span v-else>{{ userInfo.age || '未填写' }}</span>
        </el-descriptions-item>

        <el-descriptions-item label="注册时间">
          {{ formatDate(userInfo.createTime) }}
        </el-descriptions-item>

        <el-descriptions-item label="更新时间">
          {{ formatDate(userInfo.updateTime) }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="isEditing" style="margin-top: 20px; text-align: center;">
        <el-button type="success" size="large" @click="handleSave" :loading="submitLoading">保存修改</el-button>
      </div>
    </el-card>

    <el-dialog v-model="emailDialogVisible" title="修改绑定邮箱" width="400px" destroy-on-close>
      <el-form :model="emailForm" label-position="top">
        <el-form-item label="新邮箱地址">
          <el-input v-model="emailForm.newEmail" placeholder="请输入新邮箱" />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display: flex; gap: 10px;">
            <el-input v-model="emailForm.code" placeholder="6位验证码" />
            <el-button :disabled="countdown > 0" @click="handleSendUpdateCode(emailForm.newEmail)">
              {{ countdown > 0 ? `${countdown}s` : '获取' }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emailDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEmailChange" :loading="submitLoading">确定修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdDialogVisible" title="重置登录密码" width="400px" destroy-on-close>
      <el-form :model="pwdForm" label-position="top">
        <el-form-item label="当前邮箱">
          <el-input :value="userInfo.email" disabled />
        </el-form-item>
        <el-form-item label="验证码">
          <div style="display: flex; gap: 10px;">
            <el-input v-model="pwdForm.code" placeholder="请输入验证码" />
            <el-button :disabled="countdown > 0" @click="handleSendUpdateCode(userInfo.email)">
              {{ countdown > 0 ? `${countdown}s` : '获取' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少6位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPwdChange" :loading="submitLoading">更新密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/index.js'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)         // 页面初始加载状态
const submitLoading = ref(false)   // 按钮提交状态
const isEditing = ref(false)
const userInfo = ref({})
const editForm = ref({})

// 弹窗控制
const emailDialogVisible = ref(false)
const pwdDialogVisible = ref(false)
const countdown = ref(0)
let timer = null

const emailForm = ref({ newEmail: '', code: '' })
const pwdForm = ref({ newPassword: '', code: '' })

// 初始化获取数据
const fetchUserInfo = async () => {
  loading.value = true
  try {
    const res = await userApi.getMe()
    if (res.code === 200) {
      userInfo.value = res.data
      editForm.value = { ...res.data }
    }
  } catch (error) {
    console.error("获取用户信息失败", error)
  } finally {
    loading.value = false
  }
}

const toggleEdit = () => {
  if (isEditing.value) editForm.value = { ...userInfo.value }
  isEditing.value = !isEditing.value
}

// 资料保存（同步数据库操作）
const handleSave = async () => {
  submitLoading.value = true
  try {
    const res = await userApi.updateMe(editForm.value)
    if (res.code === 200) {
      ElMessage.success('个人资料更新成功')
      isEditing.value = false
      await fetchUserInfo()
    } else {
      ElMessage.error(res.message || '更新失败')
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '服务器连接失败')
  } finally {
    submitLoading.value = false
  }
}

// 验证码发送（先显示倒计时，再后台请求）
const handleSendUpdateCode = async (targetEmail) => {
  if (!targetEmail) return ElMessage.warning("邮箱地址不能为空")

  // 1. 立即启动前端倒计时（消除卡顿感）
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)

  try {
    // 2. 后台发送请求
    await userApi.sendCode(targetEmail, 'update')
    ElMessage.success("验证码已发送至邮箱")
  } catch (err) {
    // 3. 失败时清除倒计时
    if (timer) clearInterval(timer)
    countdown.value = 0
    ElMessage.error(err.response?.data?.message || "验证码发送失败")
  }
}

// 修改邮箱
const openEmailDialog = () => {
  emailForm.value = { newEmail: '', code: '' }
  emailDialogVisible.value = true
}

const submitEmailChange = async () => {
  if (!emailForm.value.code || !emailForm.value.newEmail) {
    return ElMessage.error("请填写完整信息")
  }
  submitLoading.value = true
  try {
    const res = await userApi.updateMe({
      id: userInfo.value.id,
      email: emailForm.value.newEmail,
      code: emailForm.value.code
    })
    if (res.code === 200) {
      ElMessage.success("邮箱修改成功")
      emailDialogVisible.value = false
      await fetchUserInfo()
    } else {
      ElMessage.error(res.message)
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || "修改请求失败")
  } finally {
    submitLoading.value = false
  }
}

// 修改密码
const openPwdDialog = () => {
  pwdForm.value = { newPassword: '', code: '' }
  pwdDialogVisible.value = true
}

const submitPwdChange = async () => {
  if (!pwdForm.value.code || !pwdForm.value.newPassword) {
    return ElMessage.error("请填写完整信息")
  }
  if (pwdForm.value.newPassword.length < 6) {
    return ElMessage.error("新密码至少需要 6 位")
  }
  submitLoading.value = true
  try {
    const res = await userApi.updateMe({
      id: userInfo.value.id,
      password: pwdForm.value.newPassword,
      code: pwdForm.value.code
    })
    if (res.code === 200) {
      ElMessage.success("密码修改成功，请重新登录")
      pwdDialogVisible.value = false
      localStorage.removeItem('token')
      router.push('/login')
    } else {
      ElMessage.error(res.message)
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || "更新密码失败")
  } finally {
    submitLoading.value = false
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return '未设置'
  const date = new Date(dateStr)
  return isNaN(date.getTime()) ? '格式错误' : date.toLocaleString()
}

onMounted(() => fetchUserInfo())
</script>

<style scoped>
.user-detail-container {
  max-width: 800px;
  margin: 20px auto;
}
.card-header {
  line-height: 32px;
}
.action-btns .el-button {
  margin-left: 10px;
}
</style>