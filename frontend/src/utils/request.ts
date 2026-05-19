import axios from 'axios';
import { ElMessage } from 'element-plus';
import router from '@/router'

const request = axios.create({
    baseURL: '/api',
    timeout: 15000,
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config;
  },
    (error) => {
        return Promise.reject(error);
    }
)

// 响应拦截器
request.interceptors.response.use(
    (response) => {
        return response.data;
    },
    (error) => {
        if (error.response) {
            const {status, data} = error.response;
            if (status === 401 || status === 403) {
                localStorage.removeItem('token');
                router.push('/login');
            } else {
                ElMessage.error(data.message || '请求失败');
            }
        } else {
            ElMessage.error('网络错误');
        }
        return Promise.reject(error);
    }
)

export default request;
