<script setup lang="ts">
const year = new Date().getFullYear();
</script>

<template>
  <div id="userLayout">
    <aside class="brand">
      <!-- logo.png 本身就是带字的横版字标，不再另配文字 -->
      <!-- logo-mark.png 是从 logo.png 抠掉白底、裁掉留白的版本，
           原图四周近 60% 是空白，直接用会在深色面板上显出一块白方块 -->
      <img class="logo" src="@/assets/logo-mark.png" alt="Byte Judge" />
      <p class="tagline">在线判题，即时反馈</p>
    </aside>
    <main class="panel">
      <div class="panel-inner">
        <!-- 子路由（登录 / 注册）渲染在这里，布局本身不含表单 -->
        <router-view />
      </div>
      <footer class="footer">© {{ year }} Jack</footer>
    </main>
  </div>
</template>

<style scoped>
/*
 * 登录注册是独立的一套布局，配色取自 logo 本身的蓝 → 青绿渐变，
 * 而不是 Arco 的主题变量。这是刻意的：品牌面板是固定的视觉身份，
 * 跟随暗色主题反色会让这块海军蓝变成浑浊的浅色。
 */
#userLayout {
  --auth-navy: #0e1b3d;
  --auth-navy-soft: #16357a;
  --auth-blue: #2f6bff;
  --auth-teal: #14b89a;
  --auth-muted: #6b7a99;
  --auth-line: #e3eaf6;
  display: flex;
  min-height: 100vh;
  background-color: #fff;
}

#userLayout .brand {
  display: flex;
  flex: 0 0 46%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  max-width: 560px;
  padding: 48px;
  background: linear-gradient(
    165deg,
    var(--auth-navy-soft) 0%,
    var(--auth-navy) 62%,
    #0c3347 100%
  );
}

/* 图片四周留白很大，字标实际只占中间一截，所以宽度给足才看得清 */
#userLayout .logo {
  width: min(320px, 100%);
  height: auto;
}

#userLayout .tagline {
  margin: 4px 0 0;
  color: rgb(255 255 255 / 0.72);
  font-size: 14px;
  letter-spacing: 0.4em;
}

#userLayout .panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  padding: 48px 24px;
}

#userLayout .panel-inner {
  width: 100%;
  max-width: 380px;
  margin: 0 auto;
  animation: rise 0.5s ease both;
}

#userLayout .footer {
  margin-top: 48px;
  color: var(--auth-muted);
  font-size: 12px;
  text-align: center;
}

/* 子路由里的标题、表单、底部链接都由布局统一收口，两个页面不用各写一遍 */

#userLayout :deep(.heading) {
  margin: 0 0 28px;
  color: var(--auth-navy);
  font-size: 26px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

#userLayout :deep(.arco-form-item-label) {
  color: var(--auth-muted);
  font-size: 13px;
}

#userLayout :deep(.arco-input-wrapper) {
  height: 44px;
  background-color: #fff;
  border-color: var(--auth-line);
  border-radius: 10px;
}

#userLayout :deep(.arco-input-wrapper:focus-within) {
  border-color: var(--auth-teal);
  box-shadow: 0 0 0 3px rgb(20 184 154 / 0.15);
}

#userLayout :deep(.arco-btn-primary) {
  height: 46px;
  margin-top: 8px;
  font-size: 15px;
  background-color: var(--auth-blue);
  border: none;
  border-radius: 10px;
}

#userLayout :deep(.arco-btn-primary:hover) {
  background-color: #1f5cf0;
}

#userLayout :deep(.switch) {
  margin-top: 20px;
  color: var(--auth-muted);
  font-size: 13px;
  text-align: center;
}

#userLayout :deep(.switch a) {
  color: var(--auth-teal);
  font-weight: 600;
  text-decoration: none;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* 窄屏退化为上品牌、下表单的单列 */
@media (max-width: 860px) {
  #userLayout {
    flex-direction: column;
  }

  #userLayout .brand {
    flex-basis: auto;
    max-width: none;
    padding: 36px 24px;
  }

  #userLayout .logo {
    width: min(240px, 80%);
  }

  #userLayout .panel {
    padding: 36px 24px 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  #userLayout .panel-inner {
    animation: none;
  }
}
</style>
