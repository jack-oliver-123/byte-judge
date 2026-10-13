import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import store, { key } from "./store";
import ArcoVue from "@arco-design/web-vue";
import "@arco-design/web-vue/dist/arco.css";
// 必须在 arco.css 之后：全局重置要能覆盖组件库，顺序反了会被 arco.css 盖掉
import "@/assets/global.css";
// ⚠️ 副作用导入，用于注册全局权限守卫（src/access/index.ts）。
// 漏掉这行不会有任何编译报错，权限会静默失效。必须在 .use(router) 之前。
import "@/access";

createApp(App).use(ArcoVue).use(store, key).use(router).mount("#app");
