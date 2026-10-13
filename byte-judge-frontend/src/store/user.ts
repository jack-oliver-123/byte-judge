import { Module } from "vuex";
import { LoginUser, RootState, UserState } from "./types";

const user: Module<UserState, RootState> = {
  namespaced: true,

  state: () => ({
    loginUser: {
      username: "未登录",
    },
  }),

  getters: {},

  actions: {
    /**
     * 远程拉取当前登录用户
     * TODO: HTTP 请求层尚未搭建，暂以本地数据占位，接通后端后替换为接口调用
     */
    async getLoginUser({ commit }) {
      commit("updateUser", { username: "Jack" });
    },
  },

  mutations: {
    updateUser(state, payload: LoginUser) {
      state.loginUser = payload;
    },
  },
};

export default user;
