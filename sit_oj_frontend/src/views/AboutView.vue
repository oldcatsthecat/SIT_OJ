<template>
  <div class="about-container">
    <el-row :gutter="20" justify="center">
      <el-col :xs="24" :sm="20" :md="16" :lg="14">
        <el-card class="about-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><InfoFilled /></el-icon>
              <span class="title">关于 SIT OJ 系统</span>
            </div>
          </template>
          <div class="intro-content">
            <p><strong>SIT OJ (Online Judge)</strong> 是一个专为程序设计爱好者、算法竞赛选手和学生设计的在线判题系统。</p>
            <p>系统支持多语言代码提交、实时判题、题目管理及比赛竞技功能，旨在提供一个公平、高效的算法练习与交流环境。</p>
          </div>
        </el-card>

        <el-card class="about-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Cpu /></el-icon>
              <span class="title">技术驱动</span>
            </div>
          </template>
          <div class="tech-stack">
            <div class="tech-item">
              <h4>前端 (Frontend)</h4>
              <el-tag>Vue 3</el-tag>
              <el-tag type="success">Vite 6</el-tag>
              <el-tag type="warning">Element Plus</el-tag>
              <el-tag type="info">Monaco Editor</el-tag>
            </div>
            <div class="tech-item">
              <h4>后端 (Backend)</h4>
              <el-tag>Spring Boot 3</el-tag>
              <el-tag type="success">MyBatis Plus</el-tag>
              <el-tag type="danger">Redis</el-tag>
              <el-tag type="warning">MySQL</el-tag>
            </div>
            <div class="tech-item">
              <h4>微服务 & 判题 (Infrastructure)</h4>
              <el-tag>Spring Cloud Alibaba</el-tag>
              <el-tag type="info">Nacos</el-tag>
              <el-tag type="success">Docker Sandbox</el-tag>
            </div>
          </div>
        </el-card>

        <el-card class="about-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><List /></el-icon>
              <span class="title">判题流程</span>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item timestamp="Step 1" placement="top" type="primary">
              <h4>代码提交</h4>
              <p>用户通过网页前端编辑器编写并提交代码。</p>
            </el-timeline-item>
            <el-timeline-item timestamp="Step 2" placement="top" type="success">
              <h4>任务分发</h4>
              <p>后端服务接收代码，将其存入数据库并推送至判题队列。</p>
            </el-timeline-item>
            <el-timeline-item timestamp="Step 3" placement="top" type="warning">
              <h4>安全沙箱运行</h4>
              <p>判题机在隔离的 Docker 容器中编译并运行代码，严格限制资源消耗。</p>
            </el-timeline-item>
            <el-timeline-item timestamp="Step 4" placement="top" type="info">
              <h4>结果反馈</h4>
              <p>对比标准输出，返回 AC / WA / TLE / SE 等判题状态。</p>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <el-card class="about-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="title">关于我们</span>
            </div>
          </template>
          <div class="intro-content">
            <p>感谢您的使用，如果遇到任何问题，可联系开发者QQ：9248489252，感谢您的宝贵意见和使用！</p>
          </div>
        </el-card>

        <div class="about-footer">
          <p>© 2026 SIT OJ Team. All rights reserved.</p>
          <p>Version: v1.0.0-beta</p>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { InfoFilled, Cpu, List } from '@element-plus/icons-vue'
</script>

<style scoped>
.about-container {
  padding: 40px 20px;
  background-color: #f8f9fa;
  min-height: calc(100vh - 60px);
}

.about-card {
  margin-bottom: 20px;
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: bold;
}

.intro-content p {
  line-height: 1.8;
  color: #606266;
  text-indent: 2em;
}

.tech-stack .tech-item {
  margin-bottom: 15px;
}

.tech-item h4 {
  margin-bottom: 10px;
  color: #303133;
}

.el-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

.about-footer {
  text-align: center;
  margin-top: 40px;
  color: #909399;
  font-size: 14px;
}

.about-footer p {
  margin: 5px 0;
}
</style>