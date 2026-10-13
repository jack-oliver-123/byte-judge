import { LoginUser } from "@/store/types";
import { ACCESS_ENUM, AccessEnum, isAccessEnum } from "./accessEnum";

/**
 * 判断当前登录用户是否具备访问某页面所需的权限。
 *
 * 刻意写成纯函数（不读 store、不碰 router）：
 * 1. 路由守卫与导航栏菜单共用同一判权源，避免出现
 *    "菜单看得见但点进去被拦"这类不一致；
 * 2. 后续引入 Vitest 可零成本单测。
 *
 * @param loginUser 当前登录用户，可能为 undefined（store 尚未初始化）
 * @param needAccess 访问所需的权限，缺省视为公开页面
 */
export function checkAccess(
  loginUser: LoginUser | undefined,
  needAccess: AccessEnum = ACCESS_ENUM.NOT_LOGIN
): boolean {
  // 公开页面，无需任何权限
  if (needAccess === ACCESS_ENUM.NOT_LOGIN) {
    return true;
  }

  // 登录态未知、未登录、或后端返回了未知角色 → 一律视作未登录（fail-closed）
  const rawRole = loginUser?.userRole;
  const userRole: AccessEnum = isAccessEnum(rawRole)
    ? rawRole
    : ACCESS_ENUM.NOT_LOGIN;

  // ───────────────────────────────────────────────────────────────
  // TODO: 此处的判权语义留给你决定。
  //
  // 走到这里时可以确定：userRole 与 needAccess 都是合法的 AccessEnum，
  // 且 needAccess 不是 NOT_LOGIN（上面已提前返回）。
  // userRole 可能是 USER / ADMIN / BAN / NOT_LOGIN；needAccess 只会是 USER / ADMIN。
  //
  // 方案 A ── 线性等级：ADMIN 自动涵盖 USER 的所有页面
  //   需要把 ACCESS_RANK 加进顶部的 import，然后：
  //   return ACCESS_RANK[userRole] >= ACCESS_RANK[needAccess];
  //
  // 方案 B ── 精确匹配：ADMIN 访问 USER 页面也会被拦
  //   return userRole === needAccess;
  //
  // 两种方案都能正确拦住 BAN（它的 rank 是 0，且不等于任何 needAccess），
  // 所以这一条不影响你的选择。
  //
  // 下面的 return false 只是让代码能编译的占位（fail-closed），实现后请删掉。
  // 届时 ESLint 对 userRole "assigned but never used" 的告警会自动消失——
  // 这条告警就是在提醒你这里还没写完。
  // ───────────────────────────────────────────────────────────────
  return false;
}
