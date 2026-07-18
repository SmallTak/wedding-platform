import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import http, { TOKEN_KEY, USER_KEY } from '../api/http'

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY))
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY))
  const user = ref(readStoredUser())
  const setupRequired = computed(() => Boolean(user.value?.setupRequired))

  function persistUser(value) {
    user.value = value
    if (value) localStorage.setItem(USER_KEY, JSON.stringify(value))
    else localStorage.removeItem(USER_KEY)
  }

  async function login(payload) {
    const { data } = await http.post('/auth/login', payload)
    if (data.user?.accountType === 'CUSTOMER') {
      const error = new Error('CUSTOMER_CONSOLE_ACCESS_DENIED')
      error.code = 'CUSTOMER_CONSOLE_ACCESS_DENIED'
      throw error
    }
    token.value = data.accessToken
    localStorage.setItem(TOKEN_KEY, data.accessToken)
    persistUser(data.user)
    return data.user
  }

  async function fetchCurrentUser() {
    const { data } = await http.get('/auth/me')
    persistUser(data)
    return data
  }

  async function changePassword(payload) {
    const { data } = await http.put('/account/password', payload)
    persistUser(data)
    return data
  }

  async function uploadAvatar(file) {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await http.post('/account/avatar', formData)
    return data.path
  }

  async function updateProfile(payload) {
    const { data } = await http.put('/account/profile', payload)
    persistUser(data)
    return data
  }

  function hasPermission(permission) {
    return user.value?.permissions?.includes(permission) || false
  }

  function logout() {
    token.value = null
    persistUser(null)
    localStorage.removeItem(TOKEN_KEY)
  }

  return {
    token,
    user,
    setupRequired,
    login,
    fetchCurrentUser,
    changePassword,
    uploadAvatar,
    updateProfile,
    hasPermission,
    logout,
  }
})
