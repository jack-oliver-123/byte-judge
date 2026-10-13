import { RouteRecordRaw } from "vue-router";
import HomeView from "@/views/HomeView.vue";
import { ACCESS_ENUM } from "@/access/accessEnum";
import { NO_AUTH_PATH } from "@/router/paths";
// 副作用导入：扩展 RouteMeta 的类型声明，让下面的 meta 字段有类型
import "@/router/types";

/**
 * 路由表。同时被 router（@/router/index.ts）与导航栏
 * （@/components/GlobalHeader.vue）复用，故新增一条路由会自动多出一个菜单项。
 *
 * meta 字段约定见 @/router/types.ts：
 * - title      导航栏显示的文案
 * - access     访问所需的最低权限，缺省视为公开页面
 * - hideInMenu 不在导航栏中显示
 */
export const routes: Array<RouteRecordRaw> = [
  {
    path: "/",
    name: "home",
    component: HomeView,
    meta: {
      title: "主页",
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: "/about",
    name: "about",
    // route level code-splitting
    // this generates a separate chunk (about.[hash].js) for this route
    // which is lazy-loaded when the route is visited.
    component: () =>
      import(/* webpackChunkName: "about" */ "../views/AboutView.vue"),
    meta: {
      title: "关于",
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: NO_AUTH_PATH,
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
];
