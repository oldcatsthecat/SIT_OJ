import { createRouter, createWebHistory } from 'vue-router';
import { ElMessage } from 'element-plus';
import { jwtDecode } from 'jwt-decode';    // 建议直接解析 token 拿权限
import { getToken, isTokenExpired, removeToken } from '@/utils/auth';

const routes = [
    {
        path: '/',
        redirect: '/problems' // 访问根路径时，自动重定向到题目列表
    },
    {
        path: '/problems',
        name: 'ProblemList',
        component: () => import('@/views/ProblemList.vue')
    },
    {
        path: '/problem/:id',
        name: 'ProblemDetail',
        component: () => import('@/views/ProblemDetail.vue')
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/Login.vue')
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('@/views/Register.vue')
    },
    {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/UserDetail.vue')
    },

    {
        path: '/admin',
        name: 'Admin',
        component: () => import('@/views/admin/AdminLayout.vue'), // 管理端布局
        children: [
            {
                path: 'users',
                name: 'Users' ,
                component: () => import('@/views/admin/UserManagement.vue'),
                meta: { title: '用户管理', requiresAuth: true }
            },
            {
                path: 'problems',
                name: 'Problems' ,
                component: () => import('@/views/admin/ProblemManagement.vue'),
                meta: { title: '题目管理', requiresAuth: true }
            },
            {
                path: 'competitions',
                name: 'AdminCompetition',
                component: () => import('@/views/admin/AdminCompetition.vue'),
                meta: { title: '比赛管理', requiresAuth: true }
            },
            {
                path: 'judger',
                name: 'AdminJudger',
                component: () => import('@/views/admin/JudgerMonitor.vue'), // 假设你把那个组件保存在这个位置
                meta: { title: '判题机状态', requiresAuth: true }
            }
        ],
        beforeEnter: (to, from, next) => {
            // 简单的权限检查逻辑
            const token = getToken();
            if (!token || isTokenExpired()) {
                if (token) removeToken();
                ElMessage.error('请先登录');
                return next('/login');
            }

            try {
                // 直接从 token 解析角色，比读 localStorage 的 user_info 更可靠
                const decoded = jwtDecode(token);
                if (decoded.role === 'ADMIN') {
                    next();
                } else {
                    ElMessage.warning('权限不足，无法进入管理端');
                    next('/');
                }
            } catch (error) {
                removeToken();
                ElMessage.error('身份验证失效，请重新登录');
                next('/login');
            }
        }
    },
    {
        path: '/about',
        name: 'About',
        component: () => import('@/views/AboutView.vue')
    },
    {
        path: '/submissions',
        name: 'SubmissionList',
        component: () => import('@/views/SubmissionList.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/submissions/:id',
        name: 'SubmissionDetail',
        component: () => import('@/views/SubmissionDetail.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/competitions',
        name: 'CompetitionList',
        component: () => import('@/views/CompetitionList.vue')
    },
    {
        path: '/competition/:id',
        name: 'CompetitionDetail',
        component: () => import('@/views/CompetitionDetail.vue')
    }
];

export const router = createRouter({
    history: createWebHistory(),
    routes
});

// 全局前置守卫：统一处理需要登录的路由
router.beforeEach((to, from, next) => {
    const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
    if (requiresAuth) {
        const token = getToken();
        if (!token || isTokenExpired()) {
            if (token) removeToken();
            ElMessage.error('请先登录');
            return next('/login');
        }
    }
    next();
});
