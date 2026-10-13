import { Module } from "vuex";
import { LoginUser, RootState, UserState } from "./types";
import { getLoginUserApi } from "@/api/user";
import { ACCESS_ENUM, isAccessEnum } from "@/access/accessEnum";

/**
 * "已确认未登录"的用户。
 *
 * 注意它与 state 初值的区别：此处 userRole 显式为 NOT_LOGIN，
 * 而 state 初值的 userRole 是缺省的（表示"尚未确认"）。
 */
const NOT_LOGIN_USER: LoginUser = {
  username: "未登录",
  userRole: ACCESS_ENUM.NOT_LOGIN,
};

const user: Module<UserState, RootState> = {
  namespaced: true,

  state: () => ({
    loginUser: {
      // userRole 刻意缺省，表示"尚未向后端确认过登录态"。
      // 路由守卫靠这个区分来决定是否发起 getLoginUser，详见 src/access/index.ts。
      username: "未登录",
    },
  }),

  getters: {},

  actions: {
    /**
     * 远程拉取当前登录用户
     */
    async getLoginUser({ commit }) {
      try {
        const loginUserVO = await getLoginUserApi();
        commit("updateUser", {
          // 左边 username 是前端状态的命名，右边 userName 是后端 VO 的字段名。
          // 大小写不一致是刻意保留的——接口契约跟着后端走，前端状态命名归前端。
          username: loginUserVO.userName,
          // 后端返回的 userRole 在类型上不可信，收窄一次；
          // 遇到未知角色降级为未登录（fail-closed），不要降级为 USER。
          userRole: isAccessEnum(loginUserVO.userRole)
            ? loginUserVO.userRole
            : ACCESS_ENUM.NOT_LOGIN,
        });
      } catch {
        // 失败一律写回"已确认未登录"。失败来源有两类，这里不区分：
        // 1. 未登录——后端 getLoginUser 会抛 NOT_LOGIN_ERROR(40100)，
        //    注意它仍是 HTTP 200，只是 code 非 0，由 unwrap() 抛成 BusinessError；
        // 2. 网络异常 / 后端未启动——axios 自身 reject。
        //
        // ⚠️ 这一步不能省：若失败时不 commit，userRole 会永远缺省，
        // 路由守卫每次跳转都会判定为"登录态未知"并重新发起请求。
        commit("updateUser", NOT_LOGIN_USER);
      }
    },
  },

  mutations: {
    updateUser(state, payload: LoginUser) {
      state.loginUser = payload;
    },
  },
};

export default user;
