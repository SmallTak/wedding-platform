import http from './http'

export const feedbackApi = {
  list(params = {}) {
    return http.get('/feedback', { params })
  },
  options() {
    return http.get('/feedback/options')
  },
  create(payload) {
    return http.post('/feedback', payload)
  },
  update(feedbackId, payload) {
    return http.put(`/feedback/${feedbackId}`, payload)
  },
  withdraw(feedbackId, version) {
    return http.delete(`/feedback/${feedbackId}`, { params: { version } })
  },
  reply(feedbackId, payload) {
    return http.put(`/feedback/${feedbackId}/reply`, payload)
  },
  approve(feedbackId, version) {
    return http.post(`/admin/feedback/${feedbackId}/approve`, { version })
  },
  reject(feedbackId, payload) {
    return http.post(`/admin/feedback/${feedbackId}/reject`, payload)
  },
  offline(feedbackId, payload) {
    return http.post(`/admin/feedback/${feedbackId}/offline`, payload)
  },
  approveReply(feedbackId, version) {
    return http.post(`/admin/feedback/${feedbackId}/reply/approve`, { version })
  },
  rejectReply(feedbackId, payload) {
    return http.post(`/admin/feedback/${feedbackId}/reply/reject`, payload)
  },
}

export const inquiryApi = {
  list(params = {}) {
    return http.get('/admin/inquiries', { params })
  },
  update(leadId, payload) {
    return http.put(`/admin/inquiries/${leadId}`, payload)
  },
}

export const homepageApi = {
  options() {
    return http.get('/admin/site/home')
  },
  replace(items) {
    return http.put('/admin/site/home', { items })
  },
  carouselOptions() {
    return http.get('/admin/site/home/carousel')
  },
  replaceCarousel(items) {
    return http.put('/admin/site/home/carousel', { items })
  },
}

export const customerAdminApi = {
  list() {
    return http.get('/admin/customers')
  },
  updateStatus(customerId, status) {
    return http.patch(`/admin/customers/${customerId}/status`, { status })
  },
  resetPassword(customerId, initialPassword) {
    return http.post(`/admin/customers/${customerId}/reset-password`, { initialPassword })
  },
}

export const customerProjectApplicationApi = {
  list(params = {}) {
    return http.get('/admin/customer-project-applications', { params })
  },
  approve(applicationId, version) {
    return http.post(`/admin/customer-project-applications/${applicationId}/approve`, { version })
  },
  reject(applicationId, payload) {
    return http.post(`/admin/customer-project-applications/${applicationId}/reject`, payload)
  },
}

export const analyticsApi = {
  overview(days = 30) {
    return http.get('/admin/analytics/overview', { params: { days } })
  },
}

export const notificationApi = {
  list(params = {}) {
    return http.get('/notifications', { params })
  },
  unreadCount() {
    return http.get('/notifications/unread-count')
  },
  markRead(notificationId, version) {
    return http.post(`/notifications/${notificationId}/read`, { version })
  },
  markAllRead() {
    return http.post('/notifications/read-all')
  },
}
