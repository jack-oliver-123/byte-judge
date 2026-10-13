<script setup lang="ts">
import { routes } from "@/router/route";
import { useRouter } from "vue-router";
import { computed, ref } from "vue";
import { useStore } from "@/store";
import { checkAccess } from "@/access/checkAccess";
import { ACCESS_ENUM } from "@/access/accessEnum";
import { LOGIN_PATH } from "@/router/paths";
import { RouteRecordRaw } from "vue-router";

const router = useRouter();
const store = useStore();

/**
 * 收集某条路由下应出现在菜单里的子项。
 *
 * 路由表现在是「布局 → 页面」的嵌套结构，菜单要的是页面而不是布局，
 * 所以从子路由取。子路由的 path 是相对父级的（如 "about"），
 * 拼上父级路径才是能 router.push 的完整路径。
 */
const menuItemsOf = (
  route: RouteRecordRaw
): { path: string; title: string | undefined }[] => {
  const parentPath = route.path.endsWith("/") ? route.path : route.path + "/";
  return (route.children ?? [])
    .filter(
      (child) =>
        !child.meta?.hideInMenu &&
        checkAccess(store.state.user.loginUser, child.meta?.access)
    )
    .map((child) => ({
      path: parentPath + child.path,
      title: child.meta?.title,
    }));
};

/**
 * 菜单项，按布局分组。
 *
 * 一组对应一套布局：组标题取布局路由的 meta.title，缺省则该组平铺、
 * 不显示标题（主布局就是这种）。有标题的组渲染成子菜单——
 * 这就是「嵌套路由生成嵌套菜单」。
 *
 * 必须是 computed：登录态变化后菜单要跟着重算。
 * 若写成普通常量，用户登录后导航栏不会刷新，看起来像"权限没生效"。
 * 与路由守卫共用 checkAccess，保证不会出现"菜单看得见但点进去被拦"。
 */
const menuGroups = computed(() =>
  routes
    .map((route) => ({
      title: route.meta?.title,
      items: menuItemsOf(route),
    }))
    .filter((group) => group.items.length > 0)
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

const loginUser = computed(() => store.state.user.loginUser);
const isLoggedIn = computed(
  () =>
    !!loginUser.value.userRole &&
    loginUser.value.userRole !== ACCESS_ENUM.NOT_LOGIN
);
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
        <template v-for="(group, index) in menuGroups" :key="index">
          <!-- 布局路由带了 meta.title 才包一层子菜单，否则菜单项直接平铺 -->
          <a-sub-menu v-if="group.title" :key="group.title">
            <template #title>{{ group.title }}</template>
            <a-menu-item v-for="item in group.items" :key="item.path">
              {{ item.title }}
            </a-menu-item>
          </a-sub-menu>
          <a-menu-item v-else v-for="item in group.items" :key="item.path">
            {{ item.title }}
          </a-menu-item>
        </template>
      </a-menu>
    </a-col>

    <a-col flex="none">
      <!-- 未登录时给登录入口。登录页在 UserLayout 下、hideInMenu，菜单里没有它 -->
      <a-button
        v-if="!isLoggedIn"
        type="primary"
        size="small"
        @click="router.push({ path: LOGIN_PATH })"
      >
        登录
      </a-button>
      <!-- title 属性兜住被省略号截断的部分，hover 可看全名 -->
      <div v-else class="user-info" :title="loginUser.username">
        {{ loginUser.username }}
      </div>
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
