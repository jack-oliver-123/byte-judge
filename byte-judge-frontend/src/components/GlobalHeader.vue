<script setup lang="ts">
import { routes } from "@/router/route";
import { useRouter } from "vue-router";
import { ref } from "vue";
import { useStore } from "@/store";

const router = useRouter();
const store = useStore();
const doMenuClick = (key: string) => {
  router.push({
    path: key,
  });
};
//默认主页
const selectedKeys = ref(["/"]);
router.afterEach((to) => {
  selectedKeys.value = [to.path];
});
</script>

<template>
  <a-row id="globalHeader" style="margin-bottom: 16px">
    <a-col flex="auto">
      <a-menu
        mode="horizontal"
        :selected-keys="selectedKeys"
        @menu-item-click="doMenuClick"
      >
        <a-menu-item
          key="0"
          :style="{ padding: 0, marginRight: '38px' }"
          disabled
        >
          <div class="title-bar">
            <img class="logo" src="../assets/logo.png" alt="logo" />
          </div>
        </a-menu-item>
        <a-menu-item v-for="item in routes" :key="item.path">{{
          item.name
        }}</a-menu-item>
      </a-menu>
    </a-col>
    <a-col flex="100px">
      <div>{{ store.state.user.loginUser.username }}</div>
    </a-col>
  </a-row>
</template>

<style scoped>
#globalHeader {
}

#globalHeader .title-bar {
  display: flex;
  align-items: center;
}
#globalHeader .logo {
  height: 50px;
}
</style>
