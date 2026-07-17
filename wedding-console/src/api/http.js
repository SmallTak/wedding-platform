import axios from 'axios'

const TOKEN_KEY = 'wedding_console_token'
const USER_KEY = 'wedding_console_user'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config?.url?.endsWith('/auth/login')) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      const loginPath = `${import.meta.env.BASE_URL}login`.replace('//', '/')
      if (window.location.pathname !== loginPath) {
        window.location.assign(loginPath)
      }
    }
    return Promise.reject(error)
  },
)

export { TOKEN_KEY, USER_KEY }
export default http
