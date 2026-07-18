import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  CUSTOMER_TOKEN_KEY,
  CUSTOMER_USER_KEY,
  customerAccountApi,
} from '../api/customer'

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(CUSTOMER_USER_KEY))
  } catch {
    return null
  }
}

function requireCustomerAccount(user) {
  if (user?.accountType === 'CUSTOMER') return user
  const error = new Error('CUSTOMER_ACCOUNT_REQUIRED')
  error.code = 'CUSTOMER_ACCOUNT_REQUIRED'
  throw error
}

export const useCustomerAuthStore = defineStore('customer-auth', () => {
  const token = ref(localStorage.getItem(CUSTOMER_TOKEN_KEY))
  const user = ref(readStoredUser())
  const setupRequired = computed(() => Boolean(user.value?.setupRequired))

  function persistUser(value) {
    user.value = value
    if (value) localStorage.setItem(CUSTOMER_USER_KEY, JSON.stringify(value))
    else localStorage.removeItem(CUSTOMER_USER_KEY)
  }

  function persistSession(data) {
    const customer = requireCustomerAccount(data.user)
    token.value = data.accessToken
    localStorage.setItem(CUSTOMER_TOKEN_KEY, data.accessToken)
    persistUser(customer)
    return customer
  }

  async function login(payload) {
    try {
      const { data } = await customerAccountApi.login(payload)
      return persistSession(data)
    } catch (error) {
      if (error.code === 'CUSTOMER_ACCOUNT_REQUIRED') logout()
      throw error
    }
  }

  async function register(payload) {
    const { data } = await customerAccountApi.register(payload)
    return persistSession(data)
  }

  async function fetchCurrentUser() {
    const { data } = await customerAccountApi.me()
    const customer = requireCustomerAccount(data)
    persistUser(customer)
    return customer
  }

  async function updateProfile(payload) {
    const { data } = await customerAccountApi.updateProfile(payload)
    persistUser(data)
    return data
  }

  async function changePassword(payload) {
    const { data } = await customerAccountApi.changePassword(payload)
    persistUser(data)
    return data
  }

  function logout() {
    token.value = null
    persistUser(null)
    localStorage.removeItem(CUSTOMER_TOKEN_KEY)
  }

  return {
    token,
    user,
    setupRequired,
    login,
    register,
    fetchCurrentUser,
    updateProfile,
    changePassword,
    logout,
  }
})
