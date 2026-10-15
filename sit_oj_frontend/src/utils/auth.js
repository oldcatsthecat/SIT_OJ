import { jwtDecode } from 'jwt-decode';
import axios from 'axios';

const TOKEN_KEY = 'token';

// ==================== Token 存储 ====================

export function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken() {
    localStorage.removeItem(TOKEN_KEY);
}

// ==================== Token 解析 ====================

/**
 * 解码 JWT 获取 payload 信息
 * @returns {{ username: string, role: string, userId: number, exp: number, iat: number } | null}
 */
export function getTokenInfo() {
    const token = getToken();
    if (!token) return null;
    try {
        const decoded = jwtDecode(token);
        return {
            username: decoded.sub || decoded.username || '',
            role: decoded.role || '',
            userId: decoded.id || null,
            exp: decoded.exp || 0,    // 秒级时间戳
            iat: decoded.iat || 0
        };
    } catch (e) {
        return null;
    }
}

/**
 * 检查 Token 是否已过期
 * @returns {boolean} true=已过期或不存在
 */
export function isTokenExpired() {
    const info = getTokenInfo();
    if (!info || !info.exp) return true;
    // exp 是秒级时间戳，Date.now() 是毫秒级
    return (info.exp * 1000) < Date.now();
}

/**
 * 获取 Token 剩余有效时间（毫秒）
 * @returns {number} 负数表示已过期，Infinity 表示无 token
 */
export function getRemainingTime() {
    const info = getTokenInfo();
    if (!info || !info.exp) return -Infinity;
    return (info.exp * 1000) - Date.now();
}

// ==================== Token 刷新 ====================

let refreshPromise = null;

/**
 * 尝试刷新 Token
 * 使用全局 Promise 缓存，避免并发请求触发多次刷新
 * @returns {Promise<string|null>} 新 token，失败返回 null
 */
export async function refreshToken() {
    // 如果已经在刷新中，复用同一个 Promise
    if (refreshPromise) {
        return refreshPromise;
    }

    const oldToken = getToken();
    if (!oldToken) {
        return null;
    }

    refreshPromise = (async () => {
        try {
            const res = await axios.post('/api/users/refresh', null, {
                headers: { Authorization: `Bearer ${oldToken}` },
                timeout: 10000
            });
            if (res.data && res.data.code === 200 && res.data.data) {
                const newToken = res.data.data;
                setToken(newToken);
                return newToken;
            }
            return null;
        } catch (e) {
            console.error('Token 刷新失败:', e.message);
            return null;
        } finally {
            // 无论成功失败，清除 Promise 缓存，允许下次重试
            refreshPromise = null;
        }
    })();

    return refreshPromise;
}

/**
 * 清除登录状态（移除 token + 可选跳转）
 * @param {import('vue-router').Router} [router] - 可选，传入则跳转到登录页
 */
export function clearAuth(router) {
    removeToken();
    if (router) {
        const currentPath = router.currentRoute?.value?.fullPath || '';
        // 避免在登录页重复跳转
        if (currentPath !== '/login') {
            router.push('/login');
        }
    } else {
        // 无 router 时（如 API 拦截器中调用），直接通过 location 跳转
        if (window.location.pathname !== '/login') {
            window.location.href = '/login';
        }
    }
}
