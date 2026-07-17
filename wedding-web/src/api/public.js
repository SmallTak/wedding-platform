import axios from 'axios'

const http = axios.create({
  baseURL: '/api/public',
  timeout: 15000,
})

export const publicApi = {
  status() {
    return http.get('/status')
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
}
