import { ref } from 'vue'
import { defineStore } from 'pinia'
import { notificationApi } from '../api/operations'

export const useNotificationStore = defineStore('notifications', () => {
  const unreadCount = ref(0)

  async function refreshUnreadCount() {
    const { data } = await notificationApi.unreadCount()
    unreadCount.value = data.unreadCount
    return unreadCount.value
  }

  function setUnreadCount(value) {
    unreadCount.value = Math.max(0, Number(value) || 0)
  }

  function decreaseUnreadCount() {
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  return {
    unreadCount,
    refreshUnreadCount,
    setUnreadCount,
    decreaseUnreadCount,
  }
})
