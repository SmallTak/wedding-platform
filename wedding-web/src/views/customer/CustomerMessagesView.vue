<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowUpRight,
  Bell,
  Check,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  EyeOff,
  RefreshCw,
  Star,
} from '@lucide/vue'
import { customerNotificationApi } from '../../api/customer'
import { useCustomerNotificationStore } from '../../stores/customerNotifications'

const router = useRouter()
const notificationStore = useCustomerNotificationStore()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const notifications = ref([])
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const unreadOnly = ref(false)
const unreadCount = computed(() => notificationStore.unreadCount)

const typeLabels = {
  FEEDBACK_APPROVED: '客户评价',
  FEEDBACK_REJECTED: '客户评价',
  FEEDBACK_OFFLINE: '客户评价',
}

const typeIcons = {
  FEEDBACK_APPROVED: Star,
  FEEDBACK_REJECTED: Star,
  FEEDBACK_OFFLINE: EyeOff,
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
    const { data } = await customerNotificationApi.list({
      page: page.value,
      size: 12,
      unreadOnly: unreadOnly.value,
    })
    notifications.value = data.content
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
    notificationStore.setUnreadCount(data.unreadCount)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '消息记录加载失败'
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
    const { data } = await customerNotificationApi.markRead(item.id, { version: item.version })
    Object.assign(item, data)
    notificationStore.decreaseUnreadCount()
    if (unreadOnly.value) await loadNotifications()
    return true
  } catch (error) {
    if (error.response?.data?.code === 'USER_NOTIFICATION_VERSION_CONFLICT') {
      await loadNotifications()
      errorMessage.value = '消息状态已变化，请重新操作'
    } else {
      errorMessage.value = error.response?.data?.message || '消息标记失败'
    }
    return false
  } finally {
    saving.value = false
  }
}

async function markAllRead() {
  if (!unreadCount.value || saving.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    await customerNotificationApi.markAllRead()
    notificationStore.setUnreadCount(0)
    await loadNotifications()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '全部已读失败'
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
  if (item.relatedType === 'FEEDBACK') return { name: 'customer-feedback' }
  return null
}

function destinationLabel(item) {
  return item.relatedType === 'FEEDBACK' ? '查看评价记录' : '查看详情'
}

function changePage(nextPage) {
  if (nextPage < 0 || nextPage >= totalPages.value) return
  page.value = nextPage
  loadNotifications()
}

function formatDate(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="customer-page">
    <section class="customer-page-heading">
      <div>
        <p>Messages</p>
        <h1>站内消息</h1>
      </div>
      <div class="customer-heading-actions">
        <button class="customer-icon-button" type="button" aria-label="刷新消息" title="刷新" @click="loadNotifications">
          <RefreshCw :size="18" />
        </button>
        <button
          class="customer-secondary-command"
          type="button"
          :disabled="!unreadCount || saving"
          @click="markAllRead"
        >
          <CheckCheck :size="17" />
          全部已读
        </button>
      </div>
    </section>

    <section class="customer-summary-strip customer-feedback-summary" aria-label="消息概览">
      <div><span>未读消息</span><strong>{{ unreadCount }}</strong></div>
      <div><span>当前列表</span><strong>{{ totalElements }}</strong></div>
    </section>

    <section class="customer-section">
      <div class="customer-section-heading customer-notification-toolbar">
        <div>
          <h2>消息记录</h2>
          <p>与你相关的评价审核结果会在这里妥善保留。</p>
        </div>
        <label class="customer-checkbox-control">
          <input v-model="unreadOnly" type="checkbox" />
          <span>只看未读</span>
        </label>
      </div>

      <p v-if="errorMessage" class="customer-form-message error" role="alert">{{ errorMessage }}</p>
      <div v-if="loading" class="customer-empty-state">正在加载消息记录...</div>
      <div v-else-if="notifications.length" class="customer-notification-list">
        <article
          v-for="item in notifications"
          :key="item.id"
          :class="['customer-notification-item', { unread: !item.readAt }]"
        >
          <div class="customer-notification-icon">
            <component :is="typeIcons[item.type] || Bell" :size="19" />
          </div>
          <button
            class="customer-notification-body"
            type="button"
            :aria-label="`查看消息：${item.title}`"
            @click="openNotification(item)"
          >
            <div class="customer-notification-kicker">
              <span>{{ typeLabels[item.type] || '系统消息' }}</span>
              <time>{{ formatDate(item.createdAt) }}</time>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.content }}</p>
          </button>
          <div class="customer-notification-actions">
            <button
              v-if="!item.readAt"
              class="customer-icon-button"
              type="button"
              aria-label="标记为已读"
              title="标记为已读"
              :disabled="saving"
              @click="markRead(item)"
            >
              <Check :size="17" />
            </button>
            <button
              v-if="destinationFor(item)"
              class="customer-icon-button"
              type="button"
              :aria-label="destinationLabel(item)"
              :title="destinationLabel(item)"
              :disabled="saving"
              @click="openNotification(item)"
            >
              <ArrowUpRight :size="17" />
            </button>
          </div>
        </article>
      </div>
      <div v-else class="customer-empty-state">
        {{ unreadOnly ? '暂无未读消息' : '暂无站内消息' }}
      </div>

      <nav v-if="totalPages > 1" class="customer-pagination" aria-label="消息分页">
        <button type="button" aria-label="上一页" :disabled="page === 0" @click="changePage(page - 1)">
          <ChevronLeft :size="18" />
        </button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <button type="button" aria-label="下一页" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">
          <ChevronRight :size="18" />
        </button>
      </nav>
    </section>
  </div>
</template>
