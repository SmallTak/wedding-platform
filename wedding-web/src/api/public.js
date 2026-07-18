import axios from 'axios'

const http = axios.create({
  baseURL: '/api/public',
  timeout: 15000,
})

export const publicApi = {
  status() {
    return http.get('/status')
  },
  home() {
    return http.get('/home')
  },
  categories() {
    return http.get('/content/categories')
  },
  collections(params = {}) {
    return http.get('/collections', { params })
  },
  collection(collectionId) {
    return http.get(`/collections/${collectionId}`)
  },
  collectionAccess(collectionId, password) {
    return http.post(`/collections/${collectionId}/access`, { password })
  },
  projects(params = {}) {
    return http.get('/projects', { params })
  },
  project(projectId) {
    return http.get(`/projects/${projectId}`)
  },
  projectAccess(projectId, password) {
    return http.post(`/projects/${projectId}/access`, { password })
  },
  feedback(params = {}) {
    return http.get('/feedback', { params })
  },
  submitInquiry(payload) {
    return http.post('/inquiries', payload)
  },
}
