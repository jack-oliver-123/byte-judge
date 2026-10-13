<script setup lang="ts">
import { routes } from "@/router/route";
import { useRouter } from "vue-router";
import { computed, ref } from "vue";
import { useStore } from "@/store";
import { checkAccess } from "@/access/checkAccess";

const router = useRouter();
const store = useStore();

/**
 * 按权限过滤后的菜单项。
 *
 * 必须是 computed：登录态变化后菜单要跟着重算。
 * 若写成普通常量，用户登录后导航栏不会刷新，看起来像"权限没生效"。
 *
 * 与路由守卫共用 checkAccess，保证不会出现"菜单看得见但点进去被拦"。
 */
const visibleRoutes = computed(() =>
  routes.filter(
    (item) =>
      !item.meta?.hideInMenu &&
      checkAccess(store.state.user.loginUser, item.meta?.access)
  )
);

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

const username = computed(() => store.state.user.loginUser.username);
</script>

<template>
  <a-row id="globalHeader" align="center" :wrap="false">
    <a-col flex="none">
      <div class="title-bar">
        <img class="logo" src="../assets/logo.png" alt="Byte Judge" />
        <span class="title">Byte Judge</span>
      </div>
    </a-col>

    <a-col flex="auto" class="menu-col">
      <a-menu
        mode="horizontal"
        :selected-keys="selectedKeys"
        @menu-item-click="doMenuClick"
      >
        <a-menu-item v-for="item in visibleRoutes" :key="item.path">{{
          item.meta?.title
        }}</a-menu-item>
      </a-menu>
    </a-col>

    <a-col flex="none">
      <!-- title 属性兜住被省略号截断的部分，hover 可看全名 -->
      <div class="user-info" :title="username">{{ username }}</div>
    </a-col>
  </a-row>
</template>

<style scoped>
#globalHeader {
  height: 60px;
}

#globalHeader .title-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 32px;
}

#globalHeader .logo {
  height: 36px;
}

#globalHeader .title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
  white-space: nowrap;
}

/*
 * min-width: 0 是这里的关键。flex 子项的 min-width 默认是 auto，
 * 意味着它**不能收缩到内容宽度以下**；菜单项一多就会把右侧用户名挤出容器。
 * 置 0 后菜单才会老实让位。
 */
#globalHeader .menu-col {
  min-width: 0;
}

/*
 * 用户名换行的根因是外层给了固定宽度（原来是 a-col flex="100px"），
 * 名字一长就在 100px 的盒子里折行、把整个顶栏撑高。
 * 现在改成 flex="none" 由内容定宽，再用 max-width + 省略号兜住极端长度。
 */
#globalHeader .user-info {
  max-width: 160px;
  margin-left: 16px;
  overflow: hidden;
  color: var(--color-text-2);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
