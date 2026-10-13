<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<template>
  <div id="noAuthView">
    <a-result status="403" title="403" subtitle="抱歉，你没有访问该页面的权限">
      <template #extra>
        <a-button type="primary" @click="router.push({ path: '/' })">
          返回主页
        </a-button>
      </template>
    </a-result>
  </div>
</template>

<style scoped>
#noAuthView {
  padding: 48px 0;
}
</style>
