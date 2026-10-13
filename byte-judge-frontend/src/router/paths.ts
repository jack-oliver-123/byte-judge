/**
 * 路由路径常量。
 *
 * 独立成文件且不 import 任何模块，是为了打破循环依赖：
 * 守卫（access/index.ts）要跳转到这些路径，而它依赖 @/router → route.ts，
 * 若常量定义在守卫里，route.ts 反过来引用就会成环。
 *
 * 路径写错在守卫里的后果是无限重定向或 404，所以不要散落字符串字面量。
 */

/** 无权限提示页 */
export const NO_AUTH_PATH = "/noAuth";

/** 登录页 */
export const LOGIN_PATH = "/user/login";

/** 注册页 */
export const REGISTER_PATH = "/user/register";
