<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowUpRight,
  Bell,
  Check,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  Images,
  Inbox,
  MessageSquareText,
  RefreshCw,
  ShieldCheck,
} from '@lucide/vue'
import { notificationApi } from '../api/operations'
import { useAuthStore } from '../stores/auth'
import { useNotificationStore } from '../stores/notifications'
import { apiErrorMessage, formatDateTime } from '../utils/content'

const router = useRouter()
const auth = useAuthStore()
const notificationStore = useNotificationStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const notifications = ref([])
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const unreadOnly = ref(false)

const typeLabels = {
  COLLECTION_REVIEW_TASK: '作品审核',
  COLLECTION_REVIEW_APPROVED: '作品审核',
  COLLECTION_REVIEW_REJECTED: '作品审核',
  COLLECTION_PARTICIPANT_ADDED: '参与关系',
  COLLECTION_PARTICIPANT_REMOVED: '参与关系',
  COLLECTION_PUBLISHED: '内容发布',
  COLLECTION_OFFLINE: '内容发布',
  CUSTOMER_FEEDBACK_NEW: '客户评价',
  FEEDBACK_APPROVED: '客户评价',
  FEEDBACK_REJECTED: '客户评价',
  FEEDBACK_OFFLINE: '客户评价',
  FEEDBACK_REPLY_SUBMITTED: '评价回复',
  FEEDBACK_REPLY_APPROVED: '评价回复',
  FEEDBACK_REPLY_REJECTED: '评价回复',
  CONSULTATION_NEW: '咨询线索',
}

onMounted(loadNotifications)

watch(unreadOnly, () => {
  page.value = 0
  loadNotifications()
})

async function loadNotifications() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await notificationApi.list({
      page: page.value,
      size: 15,
      unreadOnly: unreadOnly.value,
    })
    notifications.value = data.content
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
    notificationStore.setUnreadCount(data.unreadCount)
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '站内消息加载失败')
  } finally {
    loading.value = false
  }
}

async function markRead(item) {
  if (item.readAt) return true
  if (saving.value) return false
  saving.value = true
  errorMessage.value = ''
  try {
    const { data } = await notificationApi.markRead(item.id, item.version)
    Object.assign(item, data)
    notificationStore.decreaseUnreadCount()
    if (unreadOnly.value) await loadNotifications()
    return true
  } catch (error) {
    if (error.response?.data?.code === 'USER_NOTIFICATION_VERSION_CONFLICT') {
      await loadNotifications()
      errorMessage.value = '消息状态已变化，请重新操作'
    } else {
      errorMessage.value = apiErrorMessage(error, '消息标记失败')
    }
    return false
  } finally {
    saving.value = false
  }
}

async function markAllRead() {
  if (!notificationStore.unreadCount || saving.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    await notificationApi.markAllRead()
    notificationStore.setUnreadCount(0)
    await loadNotifications()
  } catch (error) {
    errorMessage.value = apiErrorMessage(error, '全部已读失败')
  } finally {
    saving.value = false
  }
}

async function openNotification(item) {
  const read = await markRead(item)
  if (!read) return
  const destination = destinationFor(item)
  if (destination) await router.push(destination)
}

function destinationFor(item) {
  if (item.relatedType === 'COLLECTION_REVIEW') {
    return auth.user?.accountType === 'CREATOR' ? { name: 'collections' } : { name: 'reviews' }
  }
  if (item.relatedType === 'COLLECTION_REVIEW') {
    return { name: 'reviews' }
  }
  if (item.relatedType === 'COLLECTION') return { name: 'collections' }
  if (item.relatedType === 'FEEDBACK' || item.relatedType === 'FEEDBACK_REPLY') {
    return { name: 'feedback' }
  }
  if (item.relatedType === 'INQUIRY') return { name: 'inquiries' }
  return null
}

function iconFor(item) {
  if (item.relatedType === 'COLLECTION_REVIEW') return ShieldCheck
  if (item.relatedType === 'COLLECTION') return Images
  if (item.relatedType === 'FEEDBACK' || item.relatedType === 'FEEDBACK_REPLY') return MessageSquareText
  if (item.relatedType === 'INQUIRY') return Inbox
  return Bell
}

function changePage(nextPage) {
  if (nextPage < 0 || nextPage >= totalPages.value) return
  page.value = nextPage
  loadNotifications()
}
</script>

<template>
  <main class="dashboard-content management-content notification-content" v-loading="loading">
    <section class="management-summary notification-summary" aria-label="消息概览">
      <div><span>未读消息</span><strong>{{ notificationStore.unreadCount }}</strong></div>
      <div><span>当前列表</span><strong>{{ totalElements }}</strong></div>
    </section>

    <p v-if="errorMessage" class="dashboard-error">{{ errorMessage }}</p>

    <section class="dashboard-section management-panel">
      <div class="section-title-row notification-section-heading">
        <div><p>Messages</p><h2>消息记录</h2></div>
        <div class="notification-toolbar">
          <label>
            <input v-model="unreadOnly" type="checkbox" />
            <span>只看未读</span>
          </label>
          <button
            class="secondary-command"
            type="button"
            :disabled="!notificationStore.unreadCount || saving"
            @click="markAllRead"
          >
            <CheckCheck :size="16" />
            全部已读
          </button>
          <button class="icon-command" type="button" aria-label="刷新消息" title="刷新消息" @click="loadNotifications">
            <RefreshCw :size="17" />
          </button>
        </div>
      </div>

      <div v-if="notifications.length" class="workbench-notification-list">
        <article
          v-for="item in notifications"
          :key="item.id"
          :class="{ unread: !item.readAt }"
        >
          <div class="workbench-notification-icon">
            <component :is="iconFor(item)" :size="18" />
          </div>
          <button class="workbench-notification-body" type="button" @click="openNotification(item)">
            <div>
              <span>{{ typeLabels[item.type] || '系统消息' }}</span>
              <time>{{ formatDateTime(item.createdAt) }}</time>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.content }}</p>
          </button>
          <div class="workbench-notification-actions">
            <button
              v-if="!item.readAt"
              class="icon-command"
              type="button"
              aria-label="标记为已读"
              title="标记为已读"
              :disabled="saving"
              @click="markRead(item)"
            >
              <Check :size="16" />
            </button>
            <button
              v-if="destinationFor(item)"
              class="icon-command"
              type="button"
              aria-label="查看相关业务"
              title="查看相关业务"
              :disabled="saving"
              @click="openNotification(item)"
            >
              <ArrowUpRight :size="16" />
            </button>
          </div>
        </article>
      </div>
      <div v-else-if="!loading" class="dashboard-empty">
        {{ unreadOnly ? '暂无未读消息' : '暂无站内消息' }}
      </div>

      <nav v-if="totalPages > 1" class="notification-pagination" aria-label="消息分页">
        <button class="icon-command" type="button" aria-label="上一页" :disabled="page === 0" @click="changePage(page - 1)">
          <ChevronLeft :size="17" />
        </button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <button class="icon-command" type="button" aria-label="下一页" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
          <ChevronRight :size="17" />
        </button>
      </nav>
    </section>
  </main>
</template>
