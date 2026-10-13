import { InjectionKey } from "vue";
import { createStore, Store, useStore as baseUseStore } from "vuex";
import user from "./user";
import { RootState } from "./types";

/**
 * 注入 key。Vuex 4 默认注入的 key 不携带类型信息，
 * 必须借助 InjectionKey 才能让 useStore() 推导出 RootState。
 * 对应 main.ts 中的 app.use(store, key)。
 */
export const key: InjectionKey<Store<RootState>> = Symbol("store");

const store = createStore<RootState>({
  modules: {
    user,
  },
});

/**
 * 带类型的 useStore，组件内直接调用即可，无需重复传 key
 */
export function useStore(): Store<RootState> {
  return baseUseStore(key);
}

export default store;
