const errorMessages = {
  CATEGORY_NAME_EXISTS: '分类名称已存在',
  TAG_NAME_EXISTS: '标签名称已存在',
  CATEGORY_VERSION_CONFLICT: '分类已被其他人修改，请刷新后重试',
  TAG_VERSION_CONFLICT: '标签已被其他人修改，请刷新后重试',
  CATEGORY_IN_USE: '该分类仍被作品集使用，请先调整相关作品集',
  TAG_IN_USE: '该标签仍被作品集使用，请先调整相关作品集',
  CREATOR_VERSION_CONFLICT: '创作者账号已被其他人修改，请刷新后重试',
  COLLECTION_VERSION_CONFLICT: '作品集已被其他人修改，页面将重新加载',
  COLLECTION_PUBLISHED_LOCKED: '已发布作品集需先下架后再修改或删除',
  COLLECTION_CATEGORY_INVALID: '请选择当前启用的分类',
  COLLECTION_TAG_INVALID: '选择的标签包含已停用或无效项',
  COLLECTION_CREATOR_INVALID: '选择的共同创作者不存在、已停用或账号无效',
  IMAGE_BATCH_INVALID: '请选择 1 至 50 张有效图片',
  IMAGE_EMPTY: '图片文件为空',
  IMAGE_TOO_LARGE: '单张图片不能超过 50 MB',
  IMAGE_FORMAT_UNSUPPORTED: '仅支持真实格式为 JPEG 或 PNG 的图片',
  IMAGE_DIMENSIONS_INVALID: '图片尺寸超出处理范围',
  PHOTO_ORDER_INVALID: '图片顺序已变化，请重新加载后排序',
  COVER_PHOTO_INVALID: '所选封面图片不属于当前作品集',
  COLLECTION_PHOTOS_REQUIRED: '请先上传至少一张作品图片',
  COLLECTION_COVER_REQUIRED: '请先设置作品集封面',
  COLLECTION_PHOTOS_NOT_APPROVED: '作品集中仍有未通过审核的图片',
  COLLECTION_FIELDS_NOT_APPROVED: '作品集仍有字段未通过审核',
  COLLECTION_NOT_PENDING: '当前作品集不在待审核状态',
  COLLECTION_ALREADY_PENDING: '当前作品集已经提交审核',
  COLLECTION_NOT_READY: '作品集内容已发生变化，请重新提交并审核通过后再上架',
  COLLECTION_NOT_PUBLISHED: '当前作品集尚未发布',
  REJECTION_REASON_REQUIRED: '驳回时必须填写原因',
  PHOTO_REVIEW_SELECTION_INVALID: '请选择当前作品集中的待审核图片',
  REVIEW_ITEM_SELECTION_INVALID: '请选择当前版本中的待审核字段',
  NO_PENDING_FIELDS: '当前没有待审核字段',
  NO_REVIEW_CHANGES: '请先修改被驳回内容或新增待审内容',
  ACCESS_PASSWORD_INVALID: '访问密码需为 6 至 64 个字符，且不能超过 72 字节',
  FEEDBACK_VERSION_CONFLICT: '评价已被其他人修改，请刷新后重试',
  FEEDBACK_REPLY_VERSION_CONFLICT: '回复已被其他人修改，请刷新后重试',
  FEEDBACK_PUBLISHED_LOCKED: '已公开或已下架评价不能继续编辑',
  FEEDBACK_WITHDRAW_LOCKED: '已公开或已下架评价不能撤回',
  FEEDBACK_EDIT_ACCESS_DENIED: '只有管理员或原提交人可以修改、撤回该评价',
  FEEDBACK_REPLY_PUBLISHED_LOCKED: '已通过的公开回复不能继续编辑',
  INQUIRY_VERSION_CONFLICT: '咨询线索已被其他人更新，请刷新后重试',
  HOMEPAGE_FEATURE_TARGET_INVALID: '首页推荐只能选择当前公开发布的内容',
  HOMEPAGE_FEATURE_LIMIT: '首页最多推荐 6 条评价',
  HOMEPAGE_FEATURE_DUPLICATE: '同一内容不能重复加入首页推荐',
  HOMEPAGE_CAROUSEL_TARGET_INVALID: '轮播只能选择已审核、公开发布的作品照片',
  HOMEPAGE_CAROUSEL_DUPLICATE: '同一张照片不能重复加入首页轮播',
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
    'COLLECTION_VERSION_CONFLICT',
    'CATEGORY_VERSION_CONFLICT',
    'TAG_VERSION_CONFLICT',
    'CREATOR_VERSION_CONFLICT',
    'FEEDBACK_VERSION_CONFLICT',
    'FEEDBACK_REPLY_VERSION_CONFLICT',
    'INQUIRY_VERSION_CONFLICT',
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
  PASSWORD: '密码访问',
}

export const reviewStatusLabels = {
  DRAFT: '草稿',
  PENDING: '待审核',
  PARTIALLY_REJECTED: '部分驳回',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

export const reviewItemStatusLabels = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  REMOVED: '已移除',
}

export const reviewTaskStatusLabels = {
  PENDING: '待审核',
  PARTIALLY_REJECTED: '部分驳回',
  APPROVED: '已完成',
  CANCELLED: '已撤回',
}

export const publishStatusLabels = {
  UNPUBLISHED: '未发布',
  READY: '可发布',
  PUBLISHED: '已发布',
  OFFLINE: '已下架',
}

export function statusTone(value) {
  if (['ACTIVE', 'APPROVED', 'READY', 'PUBLISHED', 'PUBLIC', 'COMPLETED'].includes(value)) return 'positive'
  if (['REJECTED', 'PARTIALLY_REJECTED', 'DISABLED', 'INVALID'].includes(value)) return 'negative'
  if (['PENDING', 'NEW', 'FOLLOWING'].includes(value)) return 'warning'
  return 'neutral'
}
