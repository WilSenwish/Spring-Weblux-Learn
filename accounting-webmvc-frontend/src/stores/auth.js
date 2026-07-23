import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request.js'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)

  /**
   * 登录
   * @param {Object} credentials - 登录参数
   */
  async function login(credentials) {
    const res = await request.post('/auth/login', credentials)
    if (res.code === 200 && res.data) {
      token.value = res.data.token || res.data
      localStorage.setItem('token', token.value)
    }
    return res
  }

  /**
   * 注册
   * @param {Object} data - 注册参数
   */
  async function register(data) {
    return request.post('/auth/register', data)
  }

  /**
   * 退出登录
   */
  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  /**
   * 刷新 Token
   */
  async function refreshToken() {
    const res = await request.post('/auth/refresh')
    if (res.code === 200 && res.data) {
      token.value = res.data.token || res.data
      localStorage.setItem('token', token.value)
    }
    return res
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    login,
    register,
    logout,
    refreshToken
  }
})
