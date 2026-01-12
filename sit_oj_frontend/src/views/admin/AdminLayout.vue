<template>
  <div class="admin-layout">
    <el-container style="height: 100vh;">
      <el-aside width="200px" style="background-color: #304156;">
        <div class="admin-logo">SIT-OJ 管理端</div>
        <el-menu
            active-text-color="#409EFF"
            background-color="#304156"
            class="el-menu-vertical"
            default-active="1"
            text-color="#fff"
            router
        >
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/problems">
            <el-icon><Document /></el-icon>
            <span>题目管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/competitions">
            <el-icon><Document /></el-icon>
            <span>比赛管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/judger">
            <el-icon><Monitor /></el-icon>
            <span>测评机监控</span>
          </el-menu-item>
          <el-menu-item index="/">
            <el-icon><Back /></el-icon>
            <span>返回前台</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="admin-header">
          <div class="breadcrumb">后台管理系统 / {{ currentRouteName }}</div>
        </el-header>

        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { User, Document, Back } from '@element-plus/icons-vue'

const route = useRoute()
const currentRouteName = computed(() => {
  if (route.path.includes('users')) return '用户管理'
  if (route.path.includes('problems')) return '题目管理'
  return '概览'
})
</script>

<style scoped>
.admin-logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-weight: bold;
  font-size: 18px;
  background-color: #2b2f3a;
}
.admin-header {
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  padding: 0 20px;
}
.el-menu-vertical {
  border-right: none;
}
</style>