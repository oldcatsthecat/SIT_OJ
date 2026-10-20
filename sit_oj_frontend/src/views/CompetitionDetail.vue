<template>
  <div class="competition-detail-container" v-loading="loading">
    <el-card class="header-card" :class="{ 'is-finished': statusText === '已结束' }" shadow="never">
      <div class="header-content">
        <div class="info-side">
          <el-tag :type="statusType" effect="dark" class="status-badge">{{ statusText }}</el-tag>
          <h1 class="comp-title">{{ competition.competitionName || '加载中...' }}</h1>
        </div>

        <div class="timer-side" v-if="statusText !== '已结束'">
          <span class="timer-label">{{ statusText === '未开始' ? '距离开始' : '距离结束' }}</span>
          <div class="countdown-clock">
            <span class="time-num">{{ countdown.h }}</span><small>时</small>
            <span class="time-num">{{ countdown.m }}</span><small>分</small>
            <span class="time-num">{{ countdown.s }}</span><small>秒</small>
          </div>
        </div>
        <div class="timer-side" v-else>
          <span class="timer-label">比赛已于 {{ formatTime(competition.endTime) }} 结束</span>
          <div class="archive-tag"><el-icon><Calendar /></el-icon> 赛后练习模式已开放</div>
        </div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="content-tabs" type="border-card" @tab-change="handleTabChange">
      <el-tab-pane name="info">
        <template #label><el-icon><InfoFilled /></el-icon> 比赛说明</template>
        <div class="info-content">
          <h3>起止时间</h3>
          <p class="time-range">{{ formatTime(competition.startTime) }} 至 {{ formatTime(competition.endTime) }}</p>

          <h3>比赛规则</h3>
          <ul class="rule-list">
            <li><strong>排名模式：</strong>ACM / ICPC (解题数优先)</li>
            <li><strong>赛后规则：</strong>比赛结束后，题目转入练习模式，提交不更新排行榜。</li>
          </ul>

          <div class="registration-status-box">
            <div v-if="isRegistered" class="reg-success">
              <el-result icon="success" :title="statusText === '已结束' ? '已开启练习模式' : '您已报名成功'">
                <template #sub-title>
                  <p>您现在可以前往“题目列表”查看题目并进行提交。</p>
                </template>
              </el-result>
            </div>
            <div v-else class="reg-action">
              <p>{{ statusText === '已结束' ? '由于您未参加比赛，请先开启练习模式以解锁题目' : '您尚未报名参加本次比赛' }}</p>
              <el-button type="primary" size="large" @click="handleRegister" :loading="regLoading">
                {{ statusText === '已结束' ? '开启赛后练习' : '立即报名' }}
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane name="problems">
        <template #label><el-icon><List /></el-icon> 题目列表</template>

        <div v-if="!canViewProblems" class="lock-screen">
          <el-empty :description="lockMessage">
            <el-button v-if="!isLoggedIn" type="primary" size="large" @click="goToLogin">
              立即登录
            </el-button>
            <el-button v-else-if="!isRegistered" type="primary" size="large" @click="handleRegister" :loading="regLoading">
              {{ statusText === '已结束' ? '开启赛后练习查看题目' : '立即报名查看题目' }}
            </el-button>
          </el-empty>
        </div>

        <el-table
            v-else-if="competition.problems && competition.problems.length > 0"
            :data="competition.problems"
            stripe
            border
        >
          <el-table-column label="#" width="80" align="center">
            <template #default="scope">
              <span class="prob-index">{{ String.fromCharCode(65 + scope.$index) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="题目名称" min-width="250">
            <template #default="{ row }">
              <el-link type="primary" class="prob-link" @click="goToProblem(row.problemId)">
                {{ row.problemName }}
              </el-link>
              <el-tag
                  v-if="row.isSolved"
                  size="small"
                  type="success"
                  effect="dark"
              >
                已通过
              </el-tag>
              <el-tag v-if="statusText === '已结束'" size="small" type="info" class="practice-tag">练习</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="difficulty" label="难度" width="100" align="center" />
          <el-table-column label="通过/提交" width="160" align="center">
            <template #default="{ row }">
              <span class="ac-count">{{ row.acceptedNum || 0 }}</span> / {{ row.totalNum || 0 }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane name="submissions">
        <template #label><el-icon><Monitor /></el-icon> 提交记录</template>

        <div v-if="!canViewProblems" class="lock-screen">
          <el-empty :description="lockMessage">
            <el-button v-if="!isLoggedIn" type="primary" size="large" @click="goToLogin">立即登录</el-button>
            <el-button v-else-if="!isRegistered" type="primary" size="large" @click="handleRegister" :loading="regLoading">
              {{ statusText === '已结束' ? '开启赛后练习查看记录' : '立即报名查看记录' }}
            </el-button>
          </el-empty>
        </div>

        <template v-else>
          <div class="submission-filters">
            <el-input v-model="subFilters.username" placeholder="搜索用户名" clearable class="username-filter"
                      @keyup.enter="applySubmissionFilters" @clear="applySubmissionFilters" />
            <el-select v-model="subFilters.problemId" placeholder="全部题号" clearable class="select-filter"
                       @change="applySubmissionFilters">
              <el-option v-for="(problem, index) in competition.problems" :key="problem.problemId"
                         :label="`${String.fromCharCode(65 + index)} · ${problem.problemName}`"
                         :value="problem.problemId" />
            </el-select>
            <el-select v-model="subFilters.status" placeholder="全部状态" clearable class="select-filter"
                       @change="applySubmissionFilters">
              <el-option v-for="option in submissionStatuses" :key="option.value"
                         :label="option.label" :value="option.value" />
            </el-select>
            <el-button type="primary" @click="applySubmissionFilters">搜索</el-button>
          </div>
          <el-table :data="compSubmissions" v-loading="subLoading" stripe border>
            <el-table-column label="运行 ID" width="100">
              <template #default="{ row }">
                <span class="mono-font" style="color: #909399;">#{{ row.submissionId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="题目" width="80" align="center">
              <template #default="{ row }">
                <span class="prob-index">{{ getProblemLetter(row.problemId) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="用户" width="140" />
            <el-table-column label="状态" width="140">
              <template #default="{ row }">
                <el-tag :class="['status-tag', statusClass(row.status)]" effect="dark">
                  <el-icon v-if="isProcessing(row.status)" class="is-loading"><Loading /></el-icon>
                  <span>{{ row.status }}</span>
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="耗时/内存" width="160">
              <template #default="{ row }">
                <span style="font-size: 13px; color: #606266;">{{ row.timeCost }}ms / {{ row.memoryCost }}KB</span>
              </template>
            </el-table-column>
            <el-table-column label="提交时间" min-width="160">
              <template #default="{ row }">
                <span style="font-size: 13px; color: #909399;">{{ formatTime(row.submissionTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="详情" width="80" fixed="right" align="center">
              <template #default="{ row }">
                <el-button
                    v-if="row.canSeeDetail"
                    circle
                    size="small"
                    icon="View"
                    type="primary"
                    plain
                    @click="goToSubmissionDetail(row.submissionId)"
                />
                <el-icon v-else style="color: #C0C4CC"><Lock /></el-icon>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper" style="margin-top: 20px; display: flex; justify-content: center;">
            <el-pagination
                v-model:current-page="subPage.current"
                :page-size="subPage.size"
                :total="subPage.total"
                background
                layout="prev, pager, next"
                @current-change="fetchCompSubmissions"
            />
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane name="rank">
        <template #label><el-icon><Trophy /></el-icon> 排名榜</template>

        <div class="rank-content">
          <div class="rank-header-tip" v-if="isFrozen">
            <el-alert title="排行榜已封榜" :description="'比赛结束前 ' + competition.freezeMinute + ' 分钟起封榜，排名暂不更新。请联系管理员解封。'" type="error" show-icon :closable="false"/>
          </div>
          <div class="rank-header-tip" v-else-if="statusText === '已结束' && competition.freezeMinute > 0">
            <el-alert title="比赛已结束，排行榜仍处于封榜状态，等待管理员解封" type="warning" show-icon :closable="false"/>
          </div>
          <div class="rank-header-tip" v-else-if="statusText === '已结束'">
            <el-alert title="比赛已结束，排行榜已冻结。赛后的提交不会更新此榜单。" type="warning" show-icon
                      :closable="false"/>
          </div>
          <el-table :data="rankList" stripe border class="acm-rank-table">
            <el-table-column label="Rank" width="70" align="center">
              <template #default="scope">{{ (rankPage.current - 1) * rankPage.size + scope.$index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="username" label="用户" min-width="70"/>
            <el-table-column prop="realName" label="真名" min-width="70"/>
            <el-table-column prop="solvedCount" label="解题" width="80" align="center"/>
            <template v-if="competition.problems">
              <el-table-column prop="totalPenalty" label="罚时" width="80" align="center"/>
              <el-table-column
                  v-for="(prob, index) in competition.problems"
                  :key="prob.problemId"
                  :label="String.fromCharCode(65 + index)"
                  min-width="90"
                  align="center"
              >
                <template #default="{ row }">
                  <div v-if="row.submissionStats?.[prob.problemId] || frozenAttempts[row.userId]?.[prob.problemId]" class="rank-cell-wrapper">
                    <!-- AC + frozen: 绿底 +X  +  蓝底 +Y -->
                    <div v-if="row.submissionStats?.[prob.problemId]?.isAc"
                         :class="['rank-cell-status', isFirstBlood(prob.problemId, row.userId) ? 'cell-fb' : 'cell-ac']">
                      <div class="ac-mark">
                        {{ '+' + (row.submissionStats[prob.problemId].wrongAttempts + 1) }}
                        <span v-if="frozenAttempts[row.userId]?.[prob.problemId]" class="frozen-inline">
                          / +{{ frozenAttempts[row.userId][prob.problemId] }}
                        </span>
                      </div>
                      <div class="ac-time">{{ row.submissionStats[prob.problemId].acTime }}</div>
                    </div>

                    <!-- 纯封榜（无封榜前提交）: 蓝底 +Y -->
                    <div v-else-if="frozenAttempts[row.userId]?.[prob.problemId] && !row.submissionStats?.[prob.problemId]"
                         class="rank-cell-status cell-frozen">
                      <div class="frozen-mark">+{{ frozenAttempts[row.userId][prob.problemId] }}</div>
                    </div>

                    <!-- WA: 红底 -X（封榜前 WA） + 封榜后有提交则追加 /+Y -->
                    <div v-else-if="row.submissionStats?.[prob.problemId]?.wrongAttempts > 0"
                         class="rank-cell-status cell-wa">
                      <div class="wa-mark">
                        -{{ row.submissionStats[prob.problemId].wrongAttempts }}
                        <span v-if="frozenAttempts[row.userId]?.[prob.problemId]" class="frozen-inline">
                          / +{{ frozenAttempts[row.userId][prob.problemId] }}
                        </span>
                      </div>
                    </div>
                  </div>
                </template>
              </el-table-column>
            </template>
          </el-table>
          <div class="pagination-wrapper" style="margin-top: 20px; display: flex; justify-content: center;">
            <el-pagination
                v-model:current-page="rankPage.current"
                :page-size="rankPage.size"
                :total="rankPage.total"
                background
                layout="prev, pager, next"
                @current-change="fetchRank"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import {ref, onMounted, onUnmounted, computed, reactive} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {competitionApi, submissionApi} from '@/api';
import {ElMessage} from 'element-plus';
import {InfoFilled, List, Trophy, Calendar, Monitor, View, Lock, Loading} from '@element-plus/icons-vue';
import dayjs from 'dayjs';

const route = useRoute();
const router = useRouter();
const compId = route.params.id;

const loading = ref(false);
const regLoading = ref(false);
const activeTab = ref(route.query.tab || 'info');
const rankList = ref([]);
const countdown = ref({h: '00', m: '00', s: '00'});
let timer = null;

const subLoading = ref(false);
const compSubmissions = ref([]);
const subPage = reactive({current: 1, size: 15, total: 0});
const subFilters = reactive({username: '', problemId: '', status: ''});
const submissionStatuses = [
  {value: 'AC', label: 'AC'}, {value: 'WA', label: 'WA'},
  {value: 'CE', label: 'CE'}, {value: 'TLE', label: 'TLE'},
  {value: 'MLE', label: 'MLE'}, {value: 'RE', label: 'RE'},
  {value: 'SE', label: 'SE'}, {value: 'Pending', label: 'Pending'},
  {value: 'JUDGING', label: 'JUDGING'}, {value: 'Frozen', label: 'Frozen'}
];
// 轮询定时器
const subPollTimer = ref(null);

const competition = ref({
  competitionName: '',
  startTime: null,
  endTime: null,
  problems: [],
  isRegistered: false,
  registered: false
});

const isLoggedIn = computed(() => !!localStorage.getItem('token'));

const isRegistered = computed(() => {
  return competition.value.isRegistered === true || competition.value.registered === true;
});

const statusText = computed(() => {
  if (!competition.value.startTime) return '进行中';
  const now = dayjs();
  const start = dayjs(competition.value.startTime);
  const end = dayjs(competition.value.endTime);
  if (now.isBefore(start)) return '未开始';
  if (now.isAfter(end)) return '已结束';
  return '进行中';
});

const statusType = computed(() => {
  const s = statusText.value;
  if (s === '未开始') return 'info';
  if (s === '进行中') return 'success';
  return 'warning';
});

const canViewProblems = computed(() => {
  if (!isLoggedIn.value) return false;
  if (statusText.value === '未开始') return false;
  return isRegistered.value;
});

const lockMessage = computed(() => {
  if (!isLoggedIn.value) return "请先登录后再查看比赛内容";
  if (!isRegistered.value) return "需要报名或开启练习模式后方可查看内容";
  if (statusText.value === '未开始') return "比赛尚未开始";
  return "";
});

// 状态检查
const isProcessing = (status) => {
  const s = String(status || '').toUpperCase();
  return s === 'PENDING' || s === 'JUDGING';
};

const stopSubPolling = () => {
  if (subPollTimer.value) {
    clearInterval(subPollTimer.value);
    subPollTimer.value = null;
  }
};

const fetchData = async () => {
  if (!compId) return;
  loading.value = true;
  try {
    const [detailRes, statsRes] = await Promise.all([
      competitionApi.getById(compId),
      competitionApi.getProblemStats(compId)
    ]);

    if (detailRes && detailRes.code === 200) {
      const data = detailRes.data;
      const statsMap = statsRes?.data || {};
      const processedProblems = (data.problems || []).map(prob => {
        const s = statsMap[prob.problemId] || statsMap[String(prob.problemId)] || {};
        return {...prob, acceptedNum: s.acceptedNum || 0, totalNum: s.totalNum || 0};
      });

      Object.assign(competition.value, {...data, problems: processedProblems});

      if (activeTab.value === 'rank') {
        fetchRank();
      } else if (activeTab.value === 'submissions' && canViewProblems.value) {
        fetchCompSubmissions();
      }
    }
  } catch (err) {
    console.error("加载详情失败", err);
  } finally {
    loading.value = false;
  }
};

let subRequestId = 0;
const applySubmissionFilters = () => {
  subPage.current = 1;
  stopSubPolling();
  fetchCompSubmissions();
};

const fetchCompSubmissions = async (pageOrSilent = false) => {
  if (!canViewProblems.value) return;
  const isSilent = pageOrSilent === true;
  const requestId = ++subRequestId;
  if (!isSilent) subLoading.value = true;
  try {
    const res = await submissionApi.getCompetitionSubmissions({
      current: subPage.current,
      size: subPage.size,
      competitionId: compId,
      username: subFilters.username.trim() || undefined,
      problemId: subFilters.problemId || undefined,
      status: subFilters.status || undefined
    });
    if (requestId !== subRequestId) return;
    if (res && res.code === 200) {
      compSubmissions.value = res.data.records;
      subPage.total = res.data.total;

      // 检查是否需要轮询
      const needPoll = compSubmissions.value.some(s => isProcessing(s.status));
      if (needPoll) {
        if (!subPollTimer.value) {
          subPollTimer.value = setInterval(() => fetchCompSubmissions(true), 2000);
        }
      } else {
        stopSubPolling();
      }
    }
  } finally {
    if (!isSilent && requestId === subRequestId) subLoading.value = false;
  }
};

const getProblemLetter = (problemId) => {
  const idx = competition.value.problems.findIndex(p => p.problemId === problemId);
  return idx !== -1 ? String.fromCharCode(65 + idx) : '?';
};

const handleRegister = async () => {
  if (!isLoggedIn.value) return goToLogin();
  regLoading.value = true;
  try {
    const res = await competitionApi.register(compId);
    if (res.code === 200 || res.data?.code === 200) {
      ElMessage.success("操作成功");
      competition.value.isRegistered = true;
      competition.value.registered = true;
      await fetchData();
    }
  } catch (err) {
    const msg = err.response?.data?.message || "";
    if (msg.includes("已经报名")) {
      competition.value.isRegistered = true;
      ElMessage.info("您已在报名列表中");
      await fetchData();
    } else {
      ElMessage.error("操作失败");
    }
  } finally {
    regLoading.value = false;
  }
};

const goToLogin = () => {
  router.push('/login');
};

const goToProblem = (id) => {
  if (statusText.value === '已结束') {
    router.push(`/problem/${id}`);
  } else {
    router.push(`/problem/${id}?cid=${compId}`);
  }
};

const goToSubmissionDetail = (id) => {
  router.push(`/submissions/${id}`);
};

const firstBloods = ref({});

// 封榜状态
const isFrozen = ref(false);
const frozenAttempts = ref({});

// 排行榜分页
const rankPage = reactive({ current: 1, size: 20, total: 0 });
let rankPollTimer = null;
let rankRequestId = 0;

const stopRankPolling = () => {
  if (rankPollTimer) {
    clearInterval(rankPollTimer);
    rankPollTimer = null;
  }
};

const startRankPolling = () => {
  stopRankPolling();
  rankPollTimer = setInterval(() => {
    fetchRank(rankPage.current);
  }, 15000);
};

const isFirstBlood = (problemId, userId) => {
  return firstBloods.value[problemId] === userId;
};

const fetchRank = async (page = 1) => {
  const requestId = ++rankRequestId;
  try {
    rankPage.current = page;
    const res = await competitionApi.getRank(compId, { current: page, size: rankPage.size });
    if (requestId !== rankRequestId) return;
    const payload = res.data?.data || res.data || {};
    rankList.value = payload.records || [];
    rankPage.total = payload.total || 0;
    isFrozen.value = payload.isFrozen || false;
    frozenAttempts.value = payload.frozenAttempts || {};

    const fbMap = {};
    const minTimeMap = {};

    rankList.value.forEach(user => {
      if (!user.submissionStats) return;
      Object.keys(user.submissionStats).forEach(pId => {
        const stats = user.submissionStats[pId];
        if (stats.isAc) {
          if (minTimeMap[pId] === undefined || stats.acTime < minTimeMap[pId]) {
            minTimeMap[pId] = stats.acTime;
            fbMap[pId] = user.userId;
          }
        }
      });
    });
    firstBloods.value = fbMap;
  } catch (e) {
    console.error("加载排名失败", e);
  }
};

const handleTabChange = (n) => {
  stopSubPolling();
  stopRankPolling();
  if (n === 'rank') {
    fetchRank();
    startRankPolling();
  }
  if (n === 'submissions' && canViewProblems.value) fetchCompSubmissions();
  router.replace({query: {...route.query, tab: n}});
};

const statusClass = (status) => {
  const s = String(status || '').toUpperCase();
  if (s === 'AC' || s === 'ACCEPTED') return 'status-ac';
  if (s === 'WA' || s === 'WRONG ANSWER') return 'status-wa';
  if (s === 'CE' || s === 'COMPILE ERROR') return 'status-ce';
  if (s === 'TLE' || s === 'TIME LIMIT EXCEEDED') return 'status-tle';
  if (s === 'MLE' || s === 'MEMORY LIMIT EXCEEDED') return 'status-mle';
  if (s === 'RE' || s === 'RUNTIME ERROR') return 'status-re';
  if (s === 'PENDING' || s === 'JUDGING') return 'status-pending';
  return 'status-other';
};

const startTimer = () => {
  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    if (!competition.value.startTime) return;
    const now = dayjs();
    const st = statusText.value;
    let target = st === '未开始' ? dayjs(competition.value.startTime) : dayjs(competition.value.endTime);
    const diff = target.diff(now, 'second');
    if (diff <= 0) {
      countdown.value = {h: '00', m: '00', s: '00'};
      return;
    }
    countdown.value = {
      h: String(Math.floor(diff / 3600)).padStart(2, '0'),
      m: String(Math.floor((diff % 3600) / 60)).padStart(2, '0'),
      s: String(diff % 60).padStart(2, '0')
    };
  }, 1000);
};

onMounted(() => {
  fetchData();
  startTimer();
  if (activeTab.value === 'rank') startRankPolling();
});

onUnmounted(() => {
  clearInterval(timer);
  stopSubPolling();
  stopRankPolling();
});
const formatTime = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-';
</script>

<style scoped>
.competition-detail-container {
  max-width: 1200px;
  margin: 20px auto;
}

.submission-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.username-filter, .select-filter {
  width: 200px;
}

.header-card {
  margin-bottom: 20px;
  border-left: 5px solid #409eff;
}

.header-card.is-finished {
  border-left-color: #e6a23c;
  background: #fdfaf5;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
}

.info-side {
  display: flex;
  align-items: center;
  gap: 15px;
}

.comp-title {
  font-size: 22px;
  margin: 0;
  color: #303133;
}

.timer-side {
  text-align: right;
}

.countdown-clock {
  font-size: 22px;
  color: #409eff;
  font-weight: bold;
  margin-top: 5px;
}

.time-num {
  background: #303133;
  color: #fff;
  padding: 2px 6px;
  border-radius: 4px;
}

.info-content {
  padding: 20px;
}

.time-range {
  color: #409eff;
  font-weight: bold;
}

.registration-status-box {
  margin-top: 40px;
  text-align: center;
  border-top: 1px solid #eee;
  padding-top: 20px;
}

.prob-index {
  font-weight: bold;
  color: #409eff;
  font-size: 18px;
}

/* 修正 1: 状态标签 Flex 居中对齐 */
.status-tag {
  min-width: 100px;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  border: none;
}

:deep(.el-tag__content) {
  display: flex !important;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.status-ac {
  background-color: #67c23a !important;
  color: #fff;
}

.status-wa {
  background-color: #f56c6c !important;
  color: #fff;
}

.status-ce {
  background-color: #e6a23c !important;
  color: #fff;
}

.status-tle, .status-mle, .status-re {
  background-color: #909399 !important;
  color: #fff;
}

.status-pending {
  background-color: #409eff !important;
  color: #fff;
}

.status-other {
  background-color: #dcdfe6 !important;
  color: #606266;
}

/* 旋转动画 */
.is-loading {
  animation: rotating 2s linear infinite;
  font-size: 14px;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.archive-tag {
  color: #e6a23c;
  font-size: 13px;
  margin-top: 5px;
}

.lock-screen {
  padding: 40px 0;
}

.rank-cell-status {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 45px;
  width: 100%;
  border-radius: 4px;
}

.cell-ac {
  background-color: #e1f3d8 !important;
  color: #529b2e;
}

.cell-fb {
  background-color: #67c23a !important;
  color: #fff;
}

.cell-wa {
  background-color: #fef0f0 !important;
  color: #f56c6c;
}

.cell-frozen {
  background-color: #ecf5ff !important;
  color: #409eff;
}

.frozen-inline {
  color: #409eff;
  font-size: 12px;
}

.ac-mark, .wa-mark {
  font-weight: bold;
  font-size: 14px;
}

.ac-time {
  font-size: 11px;
  opacity: 0.8;
  margin-top: 2px;
}

.acm-rank-table :deep(.el-table__cell) {
  padding: 4px 0 !important;
}
</style>
