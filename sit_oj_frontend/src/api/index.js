import axios from 'axios';
import { ElMessage } from 'element-plus';

const api = axios.create({
    baseURL: 'http://localhost:8001',
    timeout: 15000
});

api.interceptors.response.use(
    response => {
        return response.data;
    },
    error => {
        if (error.response && error.response.status === 403) {
            ElMessage.error('权限不足，请联系管理员');
        }
        return Promise.reject(error);
    }
);

api.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers['Authorization'] = 'Bearer ' + token;
    }
    return config;
});

export const problemApi = {

    getList: (params) => api.get('/problems/list', { params }),

    getById: (id) => api.get(`/problems/${id}`),
};
export const userApi = {
    register: (data) => api.post('/api/users/register', data),
    login: (data) => api.post('/api/users/login', data),
    getMe: () => api.get('/api/users/me'),
    updateMe: (data) => api.put('/api/users/update', data),
    // 统一为箭头函数格式，使用模板字符串处理查询参数
    sendCode: (email, type = 'register') => api.post(`/api/users/sendCode?email=${email}&type=${type}`),
    // 统一使用 api 实例发送请求
    resetPassword: (data) => api.post('/api/users/resetPassword', data)
};

export const adminUserApi = {

    listAll: () => api.get('/api/admin/users/list'),
    delete: (id) => api.delete(`/api/admin/users/${id}`),
    update: (data) => api.put('/api/admin/users/update', data)
};

export const adminProblemApi = {
    list: () => api.get('/problems/list'),
    save: (data) => api.post('/api/admin/problems/save', data),
    delete: (id) => api.delete(`/api/admin/problems/${id}`),
    uploadTestcase: (formData) => api.post('/api/admin/problems/testcase/upload', formData, {
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

    getList: () => api.get('/competitions/list'),

    getById: (id) => api.get(`/competitions/${id}`),

    register: (id) => api.post(`/competitions/${id}/register`),

    getRank: (id) => api.get(`/competitions/${id}/rank`),

    submit: (cid, data) => api.post(`/competitions/${cid}/submit`, data),

    getProblemStats: (id) => api.get(`/competitions/${id}/stats`)



};
export const adminCompetitionApi = {

    create: (data) => api.post('api/admin/competitions/create', data),

    addProblems: (data) => api.post('api/admin/competitions/problems/add', data),

    // 新增：更新比赛
    update: (data) => api.put('/api/admin/competitions/update', data),

    // 修正：删除比赛路径
    delete: (id) => api.delete(`/api/admin/competitions/delete/${id}`),
};

export const adminJudgeApi = {
    // 获取判题机状态
    getServerStatus: () => api.get('/api/admin/judge/server_status')
};

export default api;