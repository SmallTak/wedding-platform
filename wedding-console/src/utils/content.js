const errorMessages = {
  CATEGORY_NAME_EXISTS: '分类名称已存在',
  TAG_NAME_EXISTS: '标签名称已存在',
  CATEGORY_VERSION_CONFLICT: '分类已被其他人修改，请刷新后重试',
  TAG_VERSION_CONFLICT: '标签已被其他人修改，请刷新后重试',
  PROJECT_VERSION_CONFLICT: '项目已被其他人修改，请重新打开后编辑',
  PROJECT_PUBLISHED_LOCKED: '已发布项目不能修改公共资料',
  PROJECT_CREATOR_INVALID: '选择的创作者不存在、已停用或账号无效',
  COLLECTION_VERSION_CONFLICT: '作品集已被其他人修改，页面将重新加载',
  COLLECTION_PUBLISHED_LOCKED: '已发布作品集不能修改内容或图片',
  COLLECTION_CATEGORY_INVALID: '请选择当前启用的分类',
  COLLECTION_TAG_INVALID: '选择的标签包含已停用或无效项',
  COLLECTION_PROJECT_INVALID: '关联项目不存在或当前账号不可访问',
  COLLECTION_PROJECT_ACCESS_DENIED: '当前账号未参与所选婚礼项目',
  COLLECTION_CREATOR_INVALID: '选择的共同创作者不存在、已停用或账号无效',
  COLLECTION_CREATOR_NOT_IN_PROJECT: '共同创作者必须是关联项目的参与者',
  IMAGE_BATCH_INVALID: '请选择 1 至 50 张有效图片',
  IMAGE_EMPTY: '图片文件为空',
  IMAGE_TOO_LARGE: '单张图片不能超过 30 MB',
  IMAGE_FORMAT_UNSUPPORTED: '仅支持真实格式为 JPEG 或 PNG 的图片',
  IMAGE_DIMENSIONS_INVALID: '图片尺寸超出处理范围',
  PHOTO_ORDER_INVALID: '图片顺序已变化，请重新加载后排序',
  COVER_PHOTO_INVALID: '所选封面图片不属于当前作品集',
  COLLECTION_PHOTOS_REQUIRED: '请先上传至少一张作品图片',
  COLLECTION_COVER_REQUIRED: '请先设置作品集封面',
  COLLECTION_PHOTOS_NOT_APPROVED: '作品集中仍有未通过审核的图片',
  COLLECTION_NOT_PENDING: '当前作品集不在待审核状态',
  COLLECTION_ALREADY_PENDING: '当前作品集已经提交审核',
  COLLECTION_NOT_READY: '作品集尚未达到可发布状态',
  COLLECTION_NOT_PUBLISHED: '当前作品集尚未发布',
  REJECTION_REASON_REQUIRED: '驳回时必须填写原因',
  PHOTO_REVIEW_SELECTION_INVALID: '请选择当前作品集中的待审核图片',
  PASSWORD_VISIBILITY_NOT_SUPPORTED: '密码访问尚未开放，请选择公开或隐藏',
  VERSION_CONFLICT: '数据已被其他人修改，请刷新后重试',
  VALIDATION_ERROR: '提交内容不完整或格式不正确',
}

export function apiErrorMessage(error, fallback = '操作失败') {
  const code = error.response?.data?.code
  return errorMessages[code] || error.response?.data?.message || fallback
}

export function isVersionConflict(error) {
  return [
    'VERSION_CONFLICT',
    'PROJECT_VERSION_CONFLICT',
    'COLLECTION_VERSION_CONFLICT',
    'CATEGORY_VERSION_CONFLICT',
    'TAG_VERSION_CONFLICT',
  ].includes(error.response?.data?.code)
}

export function formatDate(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN').format(new Date(value))
}

export function formatDateTime(value) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function formatFileSize(bytes) {
  if (!Number.isFinite(bytes)) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export const visibilityLabels = {
  HIDDEN: '隐藏',
  PUBLIC: '公开',
}

export const reviewStatusLabels = {
  DRAFT: '草稿',
  PENDING: '待审核',
  PARTIALLY_REJECTED: '部分驳回',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

export const publishStatusLabels = {
  UNPUBLISHED: '未发布',
  READY: '可发布',
  PUBLISHED: '已发布',
  OFFLINE: '已下架',
}

export function statusTone(value) {
  if (['ACTIVE', 'APPROVED', 'READY', 'PUBLISHED', 'PUBLIC'].includes(value)) return 'positive'
  if (['REJECTED', 'PARTIALLY_REJECTED', 'DISABLED'].includes(value)) return 'negative'
  if (['PENDING'].includes(value)) return 'warning'
  return 'neutral'
}
