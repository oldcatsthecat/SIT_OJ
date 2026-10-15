import axios from 'axios';
import { ElMessage } from 'element-plus';
import { getToken, refreshToken, clearAuth } from '@/utils/auth';

const api = axios.create({

    baseURL: '/api', // 统一前缀
    timeout: 15000
});

// ==================== 请求拦截器 ====================

api.interceptors.request.use(config => {
    const token = getToken();
    if (token) {
        config.headers['Authorization'] = 'Bearer ' + token;
    }
    return config;
});

// ==================== Token 刷新状态管理 ====================

let isRefreshing = false;
let pendingRequests = []; // 等待刷新的请求队列

/**
 * 处理刷新成功后的请求重试
 */
function onRefreshSuccess(newToken) {
    pendingRequests.forEach(({ resolve, config }) => {
        config.headers['Authorization'] = 'Bearer ' + newToken;
        resolve(api(config));
    });
    pendingRequests = [];
}

/**
 * 处理刷新失败后的请求拒绝
 */
function onRefreshFailure(error) {
    pendingRequests.forEach(({ reject }) => {
        reject(error);
    });
    pendingRequests = [];
}

// ==================== 响应拦截器 ====================

api.interceptors.response.use(
    response => {
        // 检查业务层面的认证错误
        const data = response.data;
        if (data && data.code !== 200) {
            const authKeywords = ['登录', 'Token', 'token', '失效', '过期', '认证'];
            const isAuthError = authKeywords.some(kw =>
                data.message && data.message.includes(kw)
            );
            if (isAuthError) {
                // 触发静默刷新，不弹错误提示
                return handleAuthError(response.config);
            }
        }
        return data;
    },
    error => {
        // HTTP 层面的错误
        if (error.response) {
            const status = error.response.status;
            if (status === 403) {
                ElMessage.error('权限不足，请联系管理员');
            } else if (status === 401) {
                // 401 未授权，尝试刷新 token
                return handleAuthError(error.config);
            }
        }
        return Promise.reject(error);
    }
);

/**
 * 处理认证错误：尝试刷新 token 并重试请求
 */
async function handleAuthError(failedConfig) {
    // 如果正在刷新中，将请求加入等待队列
    if (isRefreshing) {
        return new Promise((resolve, reject) => {
            pendingRequests.push({ resolve, reject, config: failedConfig });
        });
    }

    isRefreshing = true;

    try {
        const newToken = await refreshToken();
        if (newToken) {
            // 刷新成功，重试当前请求和所有等待的请求
            onRefreshSuccess(newToken);
            // 重试当前请求（因为当前请求不在 pendingRequests 中）
            failedConfig.headers['Authorization'] = 'Bearer ' + newToken;
            return api(failedConfig);
        } else {
            // 刷新失败，清除登录状态
            onRefreshFailure(new Error('Token 刷新失败'));
            ElMessage.error('登录已过期，请重新登录');
            // 延迟导入避免循环依赖
            clearAuth();
            return Promise.reject(new Error('Token 刷新失败'));
        }
    } catch (e) {
        onRefreshFailure(e);
        ElMessage.error('登录已过期，请重新登录');
        import('@/utils/auth').then(({ clearAuth }) => {
            clearAuth();
        });
        return Promise.reject(e);
    } finally {
        isRefreshing = false;
    }
}

export const problemApi = {

    getList: (params) => api.get('/problems/list', { params }),

    getById: (id, competitionId) => api.get(`/problems/${id}`, { params: { competitionId } }),
};
export const userApi = {
    register: (data) => api.post('/users/register', data),
    login: (data) => api.post('/users/login', data),
    getMe: () => api.get('/users/me'),
    updateMe: (data) => api.put('/users/update', data),
    // 统一为箭头函数格式，使用模板字符串处理查询参数
    sendCode: (email, type = 'register') => api.post('/users/sendCode', null, { params: { email, type } }),
    // 统一使用 api 实例发送请求
    resetPassword: (data) => api.post('/users/resetPassword', data)
};

export const adminUserApi = {

    listAll: (params) => api.get('/admin/users/list', { params }),
    delete: (id) => api.delete(`/admin/users/${id}`),
    setLocked: (id, locked) => api.put(`/admin/users/${id}/locked`, { locked }),
    update: (data) => api.put('/admin/users/update', data)
};

export const adminProblemApi = {
    list: (params) => api.get('/problems/list', { params }),
    save: (data) => api.post('/admin/problems/save', data),
    delete: (id) => api.delete(`/admin/problems/${id}`),
    uploadTestcase: (formData) => api.post('/admin/problems/testcase/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
};

export const submissionApi = {
    submit: (data) => api.post('/submissions/submit', data, { timeout: 60000 }),

    getList: (params) => api.get('/submissions/list', { params }),

    getById: (id) => api.get(`/submissions/${id}`),

    getCompetitionSubmissions: (params) => api.get('/submissions/competition/list', { params })

};


export const competitionApi = {

    getList: (params) => api.get('/competitions/list', { params }),

    getById: (id) => api.get(`/competitions/${id}`),

    register: (id) => api.post(`/competitions/${id}/register`),

    getRank: (id, params) => api.get(`/competitions/${id}/rank`, { params }),

    submit: (cid, data) => api.post(`/competitions/${cid}/submit`, data),

    getProblemStats: (id) => api.get(`/competitions/${id}/stats`)



};
export const adminCompetitionApi = {

    create: (data) => api.post('/admin/competitions/create', data),

    addProblems: (data) => api.post('/admin/competitions/problems/add', data),

    update: (data) => api.put('/admin/competitions/update', data),

    delete: (id) => api.delete(`/admin/competitions/delete/${id}`),

    unfreeze: (id) => api.post(`/admin/competitions/${id}/unfreeze`),

    getResolverParticipants: (id) => api.get(`/admin/competitions/${id}/export/participants`),

    exportResolver: (id, userGroups = {}) => api.post(`/admin/competitions/${id}/export`, { userGroups }),
};

export const adminJudgeApi = {
    // 获取判题机状态
    getServerStatus: () => api.get('/admin/judge/server_status')
};

export default api;
