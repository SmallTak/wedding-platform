import axios from 'axios'

export const CUSTOMER_TOKEN_KEY = 'wedding_customer_token'
export const CUSTOMER_USER_KEY = 'wedding_customer_user'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(CUSTOMER_TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const authRequest = ['/auth/login', '/auth/register'].some((path) =>
      error.config?.url?.endsWith(path),
    )
    if (error.response?.status === 401 && !authRequest) {
      localStorage.removeItem(CUSTOMER_TOKEN_KEY)
      localStorage.removeItem(CUSTOMER_USER_KEY)
      if (window.location.pathname.startsWith('/customer')
          && !window.location.pathname.startsWith('/customer/login')) {
        window.location.assign('/customer/login')
      }
    }
    return Promise.reject(error)
  },
)

export const customerAccountApi = {
  login(payload) {
    return http.post('/auth/login', payload)
  },
  register(payload) {
    return http.post('/auth/register', payload)
  },
  me() {
    return http.get('/auth/me')
  },
  updateProfile(payload) {
    return http.put('/account/customer-profile', payload)
  },
  changePassword(payload) {
    return http.put('/account/password', payload)
  },
}

export const customerProjectApi = {
  applications() {
    return http.get('/customer/projects/applications')
  },
  linked() {
    return http.get('/customer/projects/linked')
  },
  apply(payload) {
    return http.post('/customer/projects/applications', payload)
  },
}

export const customerFeedbackApi = {
  list(params = {}) {
    return http.get('/customer/feedback', { params })
  },
  options() {
    return http.get('/customer/feedback/options')
  },
  create(payload) {
    return http.post('/customer/feedback', payload)
  },
  update(feedbackId, payload) {
    return http.put(`/customer/feedback/${feedbackId}`, payload)
  },
  withdraw(feedbackId, version) {
    return http.delete(`/customer/feedback/${feedbackId}`, { params: { version } })
  },
}

export const customerNotificationApi = {
  list(params = {}) {
    return http.get('/customer/notifications', { params })
  },
  unreadCount() {
    return http.get('/customer/notifications/unread-count')
  },
  markRead(notificationId, payload) {
    return http.post(`/customer/notifications/${notificationId}/read`, payload)
  },
  markAllRead() {
    return http.post('/customer/notifications/read-all')
  },
}

export default http
