import axios from 'axios'

const VISITOR_KEY = 'wedding_public_visitor'
let memoryVisitorId = null

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
  trackVisit(type, targetId = null) {
    return http.post('/analytics/visits', {
      visitorId: visitorId(),
      type,
      targetId,
    })
  },
}

function visitorId() {
  if (memoryVisitorId) return memoryVisitorId
  try {
    const stored = localStorage.getItem(VISITOR_KEY)
    if (stored) {
      memoryVisitorId = stored
      return stored
    }
    memoryVisitorId = createVisitorId()
    localStorage.setItem(VISITOR_KEY, memoryVisitorId)
    return memoryVisitorId
  } catch {
    memoryVisitorId = createVisitorId()
    return memoryVisitorId
  }
}

function createVisitorId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `wv_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 14)}`
}
