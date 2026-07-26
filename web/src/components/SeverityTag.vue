<template>
  <span class="sev" :class="`sev--${(severity || 'INFO').toLowerCase()}`">
    <i class="sev-dot" />{{ severity }}<b v-if="count" class="sev-count">×{{ count }}</b>
  </span>
</template>

<script setup>
defineProps({
  severity: { type: String, default: 'INFO' },
  /* 传入计数时在药丸内一体展示 "BLOCKER ×2", 避免列表页碎片化排版 */
  count: { type: Number, default: 0 }
})
</script>

<style scoped>
/* 基础样式即 INFO 配色, 未知等级自动回落 */
.sev {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.03em;
  line-height: 18px;
  white-space: nowrap;
  color: #52607a;
  background: #eef2f8;
}
.sev-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #93a3bd;
}
.sev-count {
  font-weight: 700;
}
.sev--blocker {
  color: #b91c1c;
  background: #fdeeee;
}
.sev--blocker .sev-dot {
  background: #ef4444;
}
.sev--major {
  color: #c2410c;
  background: #fff2e6;
}
.sev--major .sev-dot {
  background: #f97316;
}
.sev--minor {
  color: #a16207;
  background: #fdf6dd;
}
.sev--minor .sev-dot {
  background: #eab308;
}
</style>
