<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import { userRegisterApi } from "@/api/user";
import { LOGIN_PATH } from "@/router/paths";

const router = useRouter();

const form = reactive({
  userAccount: "",
  userPassword: "",
  checkPassword: "",
});

const submitting = ref(false);

/**
 * 注册。
 *
 * 后端注册成功只返回新用户 id，不会建立登录态（登录是另一个接口），
 * 所以这里不更新 store，注册完引导去登录页。
 * 两次密码是否一致交给表单校验，不发到后端再被拒。
 */
const handleSubmit = async () => {
  submitting.value = true;
  try {
    await userRegisterApi(form);
    Message.success("注册成功，请登录");
    router.push({ path: LOGIN_PATH });
  } catch (error) {
    Message.error(error instanceof Error ? error.message : "注册失败");
  } finally {
    submitting.value = false;
  }
};
</script>

<template>
  <div id="userRegisterView">
    <h2 class="heading">注册</h2>
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
      <a-form-item
        field="checkPassword"
        label="确认密码"
        :rules="[
          { required: true, message: '请再次输入密码' },
          {
            validator: (value: string, callback: (error?: string) => void) => {
              callback(
                value === form.userPassword ? undefined : '两次输入的密码不一致'
              );
            },
          },
        ]"
      >
        <a-input-password
          v-model="form.checkPassword"
          placeholder="请再次输入密码"
        />
      </a-form-item>
      <a-button type="primary" html-type="submit" long :loading="submitting">
        注册
      </a-button>
    </a-form>
    <div class="switch">
      已有账号？
      <router-link :to="LOGIN_PATH">去登录</router-link>
    </div>
  </div>
</template>
