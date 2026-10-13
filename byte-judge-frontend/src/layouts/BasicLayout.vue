<script setup lang="ts">
import GlobalHeader from "@/components/GlobalHeader.vue";

const year = new Date().getFullYear();
</script>

<template>
  <div id="basicLayout">
    <a-layout class="layout">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content class="content">
        <div class="container">
          <router-view />
        </div>
      </a-layout-content>
      <a-layout-footer class="footer">
        <span>Byte Judge · 在线判题系统</span>
        <span class="separator">·</span>
        <span>© {{ year }} Jack</span>
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<style scoped>
#basicLayout {
  /* 左右留白集中成一个变量：顶栏、内容、底栏三者必须严格对齐，
     散落成三份字面量后迟早会改漏一处 */
  --layout-padding-x: 24px;
}

/* Arco 的 .arco-layout 本身就是 flex column，.arco-layout-content 带 flex: 1，
   所以内容区会自动撑开、把底栏顶到底部——不需要任何 sticky / absolute 定位 */
#basicLayout .layout {
  min-height: 100vh;
  background-color: var(--color-fill-1);
}

#basicLayout .header {
  /* 顶栏吸顶：导航常驻可见。注意这是 header 该有的行为，不是 footer 的 */
  position: sticky;
  top: 0;
  z-index: 100;
  height: auto;
  padding: 0 var(--layout-padding-x);
  background-color: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border-2);
}

#basicLayout .content {
  padding: 24px var(--layout-padding-x);
}

/* 内容宽度上限。1200px 之外居中留白——题目列表、代码块在超宽屏上
   铺满整行会非常难读。想改成通栏，把 max-width 去掉即可 */
#basicLayout .container {
  max-width: 1200px;
  margin: 0 auto;
}

#basicLayout .footer {
  padding: 16px var(--layout-padding-x);
  color: var(--color-text-3);
  font-size: 13px;
  text-align: center;
  background-color: var(--color-bg-2);
  border-top: 1px solid var(--color-border-2);
}

#basicLayout .footer .separator {
  margin: 0 8px;
  color: var(--color-text-4);
}
</style>
