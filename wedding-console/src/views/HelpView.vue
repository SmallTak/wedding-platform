<script setup>
import { nextTick, ref, watch } from 'vue'
import {
  ArrowRight,
  Check,
  ChevronDown,
  Eye,
  ImagePlus,
  MessageSquareText,
  Rocket,
  Send,
  ShieldCheck,
  Upload,
} from '@lucide/vue'

const modules = ref([
  {
    id: 'collection',
    title: '作品集管理',
    subtitle: '创建作品 → 上传图片 → 提交审核',
    icon: ImagePlus,
    color: '#4f7c6e',
    open: true,
    steps: [
      { text: '进入“作品集”页面，点击“新建作品集”', detail: '填写标题、分类、标签、日期和地点后确认创建。创建后状态为“草稿”。' },
      { text: '点击作品集行的图片图标，进入图片管理', detail: '上传 JPEG/PNG 图片（单次最多 20 张），拖拽排序后保存。' },
      { text: '设置封面图', detail: '点击图片上的星标按钮指定封面，封面将作为首页网格和分享链接的缩略图。' },
      { text: '点击“提交审核”', detail: '确保有封面且排序已保存后提交。已发布作品集需先下架才能修改。' },
    ],
  },
  {
    id: 'review',
    title: '审核流程',
    subtitle: '字段审核 → 图片审核 → 通过发布',
    icon: ShieldCheck,
    color: '#8e6b3e',
    open: false,
    steps: [
      { text: '左侧导航进入“审核中心”', detail: '列表默认显示待审核内容。点击作品集的“眼睛”图标打开审核详情弹窗。' },
      { text: '审核字段内容', detail: '勾选待审字段（或“全选待审字段”）→ 点击“通过字段”或“驳回字段”。驳回需填写原因。' },
      { text: '审核图片内容', detail: '在“图片审核”区勾选待审图片 → 点击“通过图片”或“驳回图片”。驳回需填写原因。' },
      { text: '全部通过后作品集自动变为“可发布”', detail: '字段和图片均通过后无需额外操作，作品集状态自动流转。驳回项需创作者修改后重新提交。' },
    ],
  },
  {
    id: 'publish',
    title: '发布与下架',
    subtitle: '审核通过 → 公开发布 → 灵活下架',
    icon: Rocket,
    color: '#7a4a5c',
    open: false,
    steps: [
      { text: '审核中心弹窗底部点击“公开发布”', detail: '或在作品集列表页点击火箭按钮直接发布。选择公开范围（公共或密码）。' },
      { text: '发布后官网即时展示', detail: '公共作品集进入公开列表和首页。密码作品集不进入公开列表，需直接链接访问。' },
      { text: '下架操作', detail: '审核中心或作品集列表页点击“下架”按钮，填写下架原因后确认。下架后官网不再展示。' },
      { text: '重新上架', detail: '内容未修改 → 直接点击“重新上架”。内容已修改 → 重新提交审核 → 通过后重新上架。' },
    ],
  },
  {
    id: 'homepage',
    title: '首页运营',
    subtitle: '轮播配置 → 评价推荐 → 即时生效',
    icon: Eye,
    color: '#4a6a8e',
    open: false,
    steps: [
      { text: '进入“首页运营”切换到“轮播”标签', detail: '搜索已发布作品集的照片，勾选要展示的照片（最多 5 张），拖拽排序。' },
      { text: '调节照片焦点并保存', detail: '可调节每张照片的水平和垂直焦点位置，控制轮播图的构图裁剪区域。' },
      { text: '配置客户评价推荐', detail: '在“客户评价”标签中勾选已通过的评价，可排序或置顶，首页展示前 6 条。' },
      { text: '保存后即时生效', detail: '配置实时生效，刷新官网首页可见。未配置时系统自动兜底展示最新内容。' },
    ],
  },
  {
    id: 'feedback',
    title: '评价与咨询',
    subtitle: '提交评价 → 审核发布 → 回复维护',
    icon: MessageSquareText,
    color: '#6e6a4f',
    open: false,
    steps: [
      { text: '进入“客户评价”点击“新建评价”', detail: '选择作品集，填写客户姓名、评分（1-5 星）和评价内容，提交审核。' },
      { text: '审核评价', detail: '在评价列表筛选“待审核”状态 → 点击通过或驳回。通过的公开评价可在官网展示。' },
      { text: '撰写创作者回复', detail: '已通过的评价可点击“回复”撰写公开回复，回复直接展示在评价下方。' },
      { text: '跟进咨询线索', detail: '“咨询线索”页面查看客户提交的联系方式和需求，点击“跟进”记录沟通进展。' },
    ],
  },
  {
    id: 'account',
    title: '账号管理',
    subtitle: '开通账号 → 角色分配 → 启停管理',
    icon: Check,
    color: '#5c6e4f',
    open: false,
    steps: [
      { text: '进入“创作者账号”点击“开通创作者”', detail: '填写手机号和职业角色（摄影师、化妆师、策划师等），确认开通。' },
      { text: '创作者首次登录完善资料', detail: '创作者首次登录需修改临时密码、上传头像并完善个人资料后才能使用。' },
      { text: '账号启停与密码重置', detail: '停用可阻止账号登录（历史数据保留）。重置密码后生成新临时密码，下次登录需修改。' },
      { text: '客户账号由客户自助注册', detail: '管理员无法创建客户账号，但可查看、启用/停用和重置客户密码。' },
    ],
  },
])

const activeModule = ref('collection')
const animatedSteps = ref({})

watch(activeModule, async (id) => {
  animatedSteps.value = {}
  await nextTick()
  const mod = modules.value.find(m => m.id === id)
  if (!mod) return
  for (let i = 0; i < mod.steps.length; i++) {
    await new Promise(r => setTimeout(r, 200))
    animatedSteps.value[`${id}-${i}`] = true
  }
}, { immediate: true })

function selectModule(id) {
  activeModule.value = id
}
</script>

<template>
  <main class="dashboard-content help-content">
    <!-- Module Picker -->
    <nav class="help-module-bar" aria-label="操作模块">
      <button
        v-for="mod in modules"
        :key="mod.id"
        type="button"
        :class="['help-module-chip', { active: activeModule === mod.id }]"
        :style="{ '--chip-color': mod.color }"
        @click="selectModule(mod.id)"
      >
        <component :is="mod.icon" :size="18" />
        <span>{{ mod.title }}</span>
      </button>
    </nav>

    <!-- Active Module Detail -->
    <section
      v-for="mod in modules"
      v-show="activeModule === mod.id"
      :key="mod.id"
      class="help-module-detail"
    >
      <header class="help-module-header" :style="{ borderColor: mod.color }">
        <component :is="mod.icon" :size="32" :style="{ color: mod.color }" />
        <div>
          <h2>{{ mod.title }}</h2>
          <p>{{ mod.subtitle }}</p>
        </div>
      </header>

      <div class="help-steps">
        <div
          v-for="(step, index) in mod.steps"
          :key="index"
          :class="['help-step', { revealed: animatedSteps[`${mod.id}-${index}`] }]"
          :style="{ transitionDelay: `${index * 0.15}s` }"
        >
          <div class="help-step-marker" :style="{ background: mod.color }">
            <Check v-if="animatedSteps[`${mod.id}-${index}`]" :size="16" />
            <span v-else>{{ index + 1 }}</span>
          </div>
          <div class="help-step-body">
            <strong>{{ step.text }}</strong>
            <p>{{ step.detail }}</p>
          </div>
          <ArrowRight v-if="index < mod.steps.length - 1" class="help-step-arrow" :size="20" />
        </div>
      </div>
    </section>
  </main>
</template>
