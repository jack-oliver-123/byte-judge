import { AccessEnum } from "@/access/accessEnum";

/**
 * 扩展 vue-router 的 RouteMeta（TS 声明合并）。
 *
 * 不做这步的话，RouteMeta 的索引签名会把 meta.access 推导成 unknown，
 * 守卫与导航栏里到处都得写类型断言。
 *
 * 独立成文件而非写在 route.ts 里，与 store/types.ts 同一思路：
 * 类型声明不参与运行时依赖，可被任意模块引用而不引入循环。
 */
declare module "vue-router" {
  interface RouteMeta {
    /** 导航栏显示的中文标题。route.name 回归其本职——命名式跳转 */
    title?: string;
    /** 访问该页面所需的最低权限。缺省视为公开页面 */
    access?: AccessEnum;
    /** 是否在导航栏中隐藏（登录页、详情页、403 页等） */
    hideInMenu?: boolean;
  }
}

// 本文件只做类型声明，不产出运行时代码。
// 必须有一条 import/export 才能被 TS 识别为模块，从而让 declare module 生效。
export {};
