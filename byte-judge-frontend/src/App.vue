<script setup lang="ts">
import { onMounted } from "vue";

/**
 * 根组件只剩一个 <router-view />。
 *
 * 布局不再在这里选择：哪套布局包住哪个页面，由 routes.ts 的嵌套结构决定
 * （顶层路由的 component 即布局组件）。在这里写 if-else 区分布局的问题是
 * 每加一套布局都要改根组件，而路由表本来就已经表达了这个归属关系。
 */
onMounted(() => {
  // 在这里编写全局初始化逻辑
});
</script>

<template>
  <router-view />
</template>
