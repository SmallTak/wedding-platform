<script setup>
import { CheckCircle2, ChevronRight, Clock3 } from '@lucide/vue'

const metrics = [
  { label: '待审核作品', value: '12', note: '3 个新提交', tone: 'red' },
  { label: '进行中项目', value: '8', note: '本月新增 2 个', tone: 'green' },
  { label: '已发布作品', value: '46', note: '本月发布 7 个', tone: 'ink' },
  { label: '待跟进咨询', value: '5', note: '今日新增 1 条', tone: 'amber' },
]

const reviewItems = [
  { title: '林女士 & 周先生婚礼', type: '婚礼跟拍', author: '张宁、陈序', count: '48 张', submittedAt: '14:20', status: '待审核' },
  { title: '湖畔订婚宴', type: '订婚返图', author: '顾遥', count: '36 张', submittedAt: '昨天', status: '部分驳回' },
  { title: '复古新娘造型', type: '化妆造型', author: '方妍', count: '22 张', submittedAt: '7 月 15 日', status: '待审核' },
]

const projects = [
  { name: '西湖国宾馆婚礼', date: '2026-09-18', creators: 4, progress: 72 },
  { name: '莫干山草坪婚礼', date: '2026-10-02', creators: 3, progress: 46 },
  { name: '绍兴中式婚礼', date: '2026-10-20', creators: 5, progress: 28 },
]
</script>

<template>
  <main class="dashboard-content">
    <section id="overview" class="metric-grid" aria-label="运营概览">
      <article v-for="metric in metrics" :key="metric.label" class="metric-item">
        <span :class="['metric-indicator', metric.tone]"></span>
        <p>{{ metric.label }}</p>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.note }}</small>
      </article>
    </section>

    <section id="review" class="dashboard-section">
      <div class="section-title-row">
        <div><p>Review queue</p><h2>待审核内容</h2></div>
        <a href="#review" class="section-action">进入审核队列<ChevronRight :size="17" /></a>
      </div>
      <div class="review-table">
        <div class="review-row review-head">
          <span>作品</span><span>创作者</span><span>图片</span><span>提交时间</span><span>状态</span><span></span>
        </div>
        <article v-for="item in reviewItems" :key="item.title" class="review-row">
          <div><strong>{{ item.title }}</strong><small>{{ item.type }}</small></div>
          <span>{{ item.author }}</span><span>{{ item.count }}</span><span>{{ item.submittedAt }}</span>
          <span :class="['status-tag', item.status === '部分驳回' ? 'status-rejected' : 'status-pending']">
            <Clock3 v-if="item.status !== '部分驳回'" :size="14" />
            <CheckCircle2 v-else :size="14" />{{ item.status }}
          </span>
          <a href="#review" class="row-action" aria-label="查看审核详情"><ChevronRight :size="18" /></a>
        </article>
      </div>
    </section>

    <section id="projects" class="dashboard-section">
      <div class="section-title-row">
        <div><p>Active projects</p><h2>进行中的婚礼项目</h2></div>
        <span class="section-count">共 8 个</span>
      </div>
      <div class="project-list">
        <article v-for="project in projects" :key="project.name" class="project-row">
          <div><strong>{{ project.name }}</strong><span>{{ project.date }}</span></div>
          <span>{{ project.creators }} 位创作者</span>
          <div class="progress-track" aria-label="项目完成度"><i :style="{ width: `${project.progress}%` }"></i></div>
          <strong>{{ project.progress }}%</strong>
        </article>
      </div>
    </section>

    <section class="dashboard-split">
      <div class="dashboard-section compact-section">
        <div class="section-title-row"><div><p>Creators</p><h2>创作者状态</h2></div></div>
        <div class="creator-summary">
          <div><span>已开通账号</span><strong>16</strong></div>
          <div><span>本月有上传</span><strong>9</strong></div>
          <div><span>待完善资料</span><strong>2</strong></div>
        </div>
      </div>
      <div class="dashboard-section compact-section">
        <div class="section-title-row"><div><p>Messages</p><h2>最新动态</h2></div></div>
        <ul class="activity-list">
          <li><i class="green"></i><span>顾遥重新提交了 4 张图片</span><time>10分钟前</time></li>
          <li><i class="red"></i><span>新增 1 条客户项目关联申请</span><time>35分钟前</time></li>
          <li><i class="amber"></i><span>收到新的婚礼咨询</span><time>1小时前</time></li>
        </ul>
      </div>
    </section>
  </main>
</template>
