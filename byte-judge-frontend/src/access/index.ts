import { Message } from "@arco-design/web-vue";
import router from "@/router";
import store from "@/store";
import { NO_AUTH_PATH } from "@/router/paths";
import { ACCESS_ENUM } from "./accessEnum";
import { checkAccess } from "./checkAccess";

/**
 * 全局权限守卫。
 *
 * ⚠️ 本模块靠**副作用导入**生效：main.ts 必须有 `import "@/access"`，
 * 且位于 `.use(router)` 之前。漏掉这行不会有任何编译报错，权限会静默失效。
 */
router.beforeEach(async (to, from, next) => {
  // meta.access 缺省视为公开页面
  const needAccess = to.meta.access ?? ACCESS_ENUM.NOT_LOGIN;

  let loginUser = store.state.user.loginUser;

  // userRole 缺省 = 尚未向后端确认过登录态，此时先拉一次。
  //
  // 这里刻意对公开页面也 await：导航栏要显示用户名与按权限过滤菜单，
  // 不等结果会先渲染出"未登录"再闪一下。代价是首屏多等一个请求的 RTT；
  // 因为 getLoginUser 无论成败都会写回 userRole，整个会话只会请求一次。
  if (!loginUser?.userRole) {
    await store.dispatch("user/getLoginUser");
    loginUser = store.state.user.loginUser;
  }

  if (checkAccess(loginUser, needAccess)) {
    next();
    return;
  }

  // 登录页尚未实现，三种拒绝原因统一跳 403 页，只是提示文案不同。
  // 接入登录页后，把"未登录"这一支改为 next(LOGIN_PATH) 即可。
  //
  // 不会死循环：NO_AUTH_PATH 对应路由的 meta.access 是 NOT_LOGIN，
  // checkAccess 对它恒为 true，必定走上面的 next() 分支。
  if (loginUser.userRole === ACCESS_ENUM.NOT_LOGIN) {
    Message.error("请先登录");
  } else if (loginUser.userRole === ACCESS_ENUM.BAN) {
    // 封号用户是"已登录但被禁用"，若也提示"请先登录"，用户会去重新登录，
    // 而 Session 本来就是有效的，登完还是被拦——必须单独给出真实原因。
    Message.error("账号已被封禁，请联系管理员");
  } else {
    Message.error("没有访问权限");
  }
  next(NO_AUTH_PATH);
});
