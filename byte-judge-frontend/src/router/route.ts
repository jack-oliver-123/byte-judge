import { RouteRecordRaw } from "vue-router";
import { ACCESS_ENUM } from "@/access/accessEnum";
import { NO_AUTH_PATH } from "@/router/paths";
import BasicLayout from "@/layouts/BasicLayout.vue";
import UserLayout from "@/layouts/UserLayout.vue";
// 副作用导入：扩展 RouteMeta 的类型声明，让下面的 meta 字段有类型
import "@/router/types";

/**
 * 路由表。同时被 router（@/router/index.ts）与导航栏
 * （@/components/GlobalHeader.vue）复用，故新增一条路由会自动多出一个菜单项。
 *
 * 布局靠嵌套路由实现，而不是在 App.vue 里按路径 if-else 切换：
 * 顶层路由的 component 就是一套布局（BasicLayout / UserLayout），
 * 它模板里的 <router-view /> 渲染 children 里的页面。
 * 新增一套布局 = 再加一条顶层路由，App.vue 不用改。
 *
 * 顶层布局路由不带 meta.title，导航栏据此跳过它们、只渲染其子路由。
 *
 * meta 字段约定见 @/router/types.ts：
 * - title      导航栏显示的文案
 * - access     访问所需的最低权限，缺省视为公开页面
 * - hideInMenu 不在导航栏中显示
 */
export const routes: Array<RouteRecordRaw> = [
  {
    path: "/",
    component: BasicLayout,
    children: [
      {
        path: "",
        name: "home",
        component: () => import("@/views/HomeView.vue"),
        meta: {
          title: "主页",
          access: ACCESS_ENUM.NOT_LOGIN,
        },
      },
      {
        path: "about",
        name: "about",
        component: () =>
          import(/* webpackChunkName: "about" */ "@/views/AboutView.vue"),
        meta: {
          title: "关于",
          access: ACCESS_ENUM.NOT_LOGIN,
        },
      },
      {
        path: NO_AUTH_PATH.slice(1),
        name: "noAuth",
        component: () =>
          import(/* webpackChunkName: "noAuth" */ "@/views/NoAuthView.vue"),
        meta: {
          title: "无权限",
          // 必须是 NOT_LOGIN，否则守卫跳到这里时又被拦，形成无限重定向
          access: ACCESS_ENUM.NOT_LOGIN,
          hideInMenu: true,
        },
      },
    ],
  },
  {
    path: "/user",
    component: UserLayout,
    children: [
      {
        path: "login",
        name: "login",
        component: () => import("@/views/user/UserLoginView.vue"),
        meta: {
          title: "登录",
          access: ACCESS_ENUM.NOT_LOGIN,
          hideInMenu: true,
        },
      },
      {
        path: "register",
        name: "register",
        component: () => import("@/views/user/UserRegisterView.vue"),
        meta: {
          title: "注册",
          access: ACCESS_ENUM.NOT_LOGIN,
          hideInMenu: true,
        },
      },
    ],
  },
];
