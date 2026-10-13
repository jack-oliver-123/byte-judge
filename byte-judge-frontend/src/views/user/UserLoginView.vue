<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import { userLoginApi } from "@/api/user";
import { REGISTER_PATH } from "@/router/paths";
import { useStore } from "@/store";

const router = useRouter();
const store = useStore();

const form = reactive({
  userAccount: "",
  userPassword: "",
});

const submitting = ref(false);

/**
 * 登录。
 *
 * 成功后不能只跳转：守卫靠 store 里的 userRole 判断登录态，
 * 而登录接口只返回用户 id，不带角色。所以这里重新拉一次当前用户，
 * 让 userRole 从 NOT_LOGIN 变成真实角色，否则跳回主页会被当成未登录。
 */
const handleSubmit = async () => {
  submitting.value = true;
  try {
    await userLoginApi(form);
    await store.dispatch("user/getLoginUser");
    Message.success("登录成功");
    router.push({ path: "/" });
  } catch (error) {
    Message.error(error instanceof Error ? error.message : "登录失败");
  } finally {
    submitting.value = false;
  }
};
</script>

<template>
  <div id="userLoginView">
    <h2 class="heading">登录</h2>
    <a-form :model="form" layout="vertical" @submit-success="handleSubmit">
      <a-form-item
        field="userAccount"
        label="账号"
        :rules="[{ required: true, message: '请输入账号' }]"
      >
        <a-input v-model="form.userAccount" placeholder="请输入账号" />
      </a-form-item>
      <a-form-item
        field="userPassword"
        label="密码"
        :rules="[{ required: true, message: '请输入密码' }]"
      >
        <a-input-password
          v-model="form.userPassword"
          placeholder="请输入密码"
        />
      </a-form-item>
      <a-button type="primary" html-type="submit" long :loading="submitting">
        登录
      </a-button>
    </a-form>
    <div class="switch">
      还没有账号？
      <router-link :to="REGISTER_PATH">去注册</router-link>
    </div>
  </div>
</template>
