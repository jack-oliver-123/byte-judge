/**
 * 当前登录用户
 */
export interface LoginUser {
  username: string;
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
