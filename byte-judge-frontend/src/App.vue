<script setup lang="ts">
import BasicLayout from "@/layouts/BasicLayout.vue";
import { onMounted } from "vue";

/**
 * 全局初始化逻辑的入口。
 *
 * 放在 App.vue 而非 main.ts：这里能用到组件实例、store 与 router，
 * 且只在应用挂载后执行一次，适合登录态探测、全局事件监听这类
 * 依赖 DOM 就绪的初始化。与渲染无关的插件注册仍留在 main.ts。
 */
onMounted(() => {
  // 在这里编写全局初始化逻辑
});
</script>

<template>
  <div id="app">
    <BasicLayout />
  </div>
</template>

<style scoped>
#app {
}
</style>
