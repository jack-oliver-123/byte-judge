import { AccessEnum } from "@/access/accessEnum";

/**
 * 当前登录用户
 */
export interface LoginUser {
  username: string;
  /**
   * 权限角色。
   *
   * 刻意设为可选：**字段缺省表示"尚未向后端确认过登录态"**，
   * 与"已确认且确实未登录"（userRole === NOT_LOGIN）是两种不同状态。
   * 路由守卫靠这个区分来决定是否需要发起 getLoginUser 请求，
   * 否则要么每次跳转都重复请求，要么永远拉不到用户信息。
   *
   * 详见 src/access/index.ts 与 src/store/user.ts。
   */
  userRole?: AccessEnum;
}

/**
 * user 模块的 state
 */
export interface UserState {
  loginUser: LoginUser;
}

/**
 * 根 state。根节点本身不存数据，字段均由 modules 注入，
 * 声明在此是为了让 store.state.user.xxx 能被正确推导。
 */
export interface RootState {
  user: UserState;
}
