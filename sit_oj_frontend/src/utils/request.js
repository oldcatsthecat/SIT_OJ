import axios from 'axios';

const request = axios.create({
    baseURL: '/api',
    timeout: 10000
});

// 请求拦截器
request.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        // 统一在请求头添加 Authorization 字段
        config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
}, error => Promise.reject(error));

export default request;