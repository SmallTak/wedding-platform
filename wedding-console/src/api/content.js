import http from './http'

export const projectApi = {
  list(params = {}) {
    return http.get('/projects', { params })
  },
  get(projectId) {
    return http.get(`/projects/${projectId}`)
  },
  create(payload) {
    return http.post('/projects', payload)
  },
  update(projectId, payload) {
    return http.put(`/projects/${projectId}`, payload)
  },
  delete(projectId, version) {
    return http.delete(`/projects/${projectId}`, { params: { version } })
  },
  assignCreators(projectId, payload) {
    return http.put(`/admin/projects/${projectId}/creators`, payload)
  },
  submit(projectId, version) {
    return http.post(`/projects/${projectId}/submit`, { version })
  },
  review(projectId) {
    return http.get(`/projects/${projectId}/review`)
  },
}

export const contentConfigApi = {
  categories() {
    return http.get('/admin/content/categories')
  },
  createCategory(payload) {
    return http.post('/admin/content/categories', payload)
  },
  updateCategory(categoryId, payload) {
    return http.put(`/admin/content/categories/${categoryId}`, payload)
  },
  deleteCategory(categoryId, version) {
    return http.delete(`/admin/content/categories/${categoryId}`, { params: { version } })
  },
  tags() {
    return http.get('/admin/content/tags')
  },
  createTag(payload) {
    return http.post('/admin/content/tags', payload)
  },
  updateTag(tagId, payload) {
    return http.put(`/admin/content/tags/${tagId}`, payload)
  },
  deleteTag(tagId, version) {
    return http.delete(`/admin/content/tags/${tagId}`, { params: { version } })
  },
}

export const collectionApi = {
  options() {
    return http.get('/collections/options')
  },
  list(params = {}) {
    return http.get('/collections', { params })
  },
  get(collectionId) {
    return http.get(`/collections/${collectionId}`)
  },
  create(payload) {
    return http.post('/collections', payload)
  },
  update(collectionId, payload) {
    return http.put(`/collections/${collectionId}`, payload)
  },
  delete(collectionId, version) {
    return http.delete(`/collections/${collectionId}`, { params: { version } })
  },
  assignCreators(collectionId, payload) {
    return http.put(`/admin/collections/${collectionId}/creators`, payload)
  },
  submit(collectionId, version) {
    return http.post(`/collections/${collectionId}/submit`, { version })
  },
  review(collectionId) {
    return http.get(`/collections/${collectionId}/review`)
  },
}

export const photoApi = {
  list(collectionId) {
    return http.get(`/collections/${collectionId}/photos`)
  },
  upload(collectionId, files, onUploadProgress) {
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    return http.post(`/collections/${collectionId}/photos`, formData, {
      timeout: 180000,
      onUploadProgress,
    })
  },
  reorder(collectionId, payload) {
    return http.put(`/collections/${collectionId}/photos/order`, payload)
  },
  setCover(collectionId, payload) {
    return http.put(`/collections/${collectionId}/cover`, payload)
  },
  delete(collectionId, photoId, version) {
    return http.delete(`/collections/${collectionId}/photos/${photoId}`, {
      params: { version },
    })
  },
}

export const reviewApi = {
  list(params = {}) {
    return http.get('/admin/reviews/collections', { params })
  },
  get(collectionId) {
    return http.get(`/admin/reviews/collections/${collectionId}`)
  },
  reviewPhotos(collectionId, payload) {
    return http.put(`/admin/reviews/collections/${collectionId}/photos`, payload)
  },
  reviewFields(collectionId, payload) {
    return http.put(`/admin/reviews/collections/${collectionId}/fields`, payload)
  },
  approve(collectionId, version) {
    return http.post(`/admin/reviews/collections/${collectionId}/approve`, { version })
  },
  reject(collectionId, payload) {
    return http.post(`/admin/reviews/collections/${collectionId}/reject`, payload)
  },
  publish(collectionId, payload) {
    return http.post(`/admin/reviews/collections/${collectionId}/publish`, payload)
  },
  offline(collectionId, payload) {
    return http.post(`/admin/reviews/collections/${collectionId}/offline`, payload)
  },
  listProjects(params = {}) {
    return http.get('/admin/reviews/projects', { params })
  },
  getProject(projectId) {
    return http.get(`/admin/reviews/projects/${projectId}`)
  },
  reviewProjectFields(projectId, payload) {
    return http.put(`/admin/reviews/projects/${projectId}/fields`, payload)
  },
  approveProject(projectId, version) {
    return http.post(`/admin/reviews/projects/${projectId}/approve`, { version })
  },
  rejectProject(projectId, payload) {
    return http.post(`/admin/reviews/projects/${projectId}/reject`, payload)
  },
  publishProject(projectId, payload) {
    return http.post(`/admin/reviews/projects/${projectId}/publish`, payload)
  },
  offlineProject(projectId, payload) {
    return http.post(`/admin/reviews/projects/${projectId}/offline`, payload)
  },
  dashboard() {
    return http.get('/admin/dashboard/overview')
  },
}
