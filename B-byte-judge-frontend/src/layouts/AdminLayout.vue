<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Code2,
  Home,
  Trophy,
  FileText,
  UsersRound,
  Settings2,
  ChevronDown,
  LogOut,
  Menu,
  UserRound,
} from '@lucide/vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
  AlertDialogAction,
} from '@/components/ui/alert-dialog'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const account = computed(() => auth.identity?.account ?? '管理员')
const mobileNav = ref(false)
const logoutOpen = ref(false)
const loggingOut = ref(false)

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logout()
    await router.replace({ name: 'login' })
  } catch {
    toast.error('退出失败，请稍后重试。')
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="admin-shell" :class="{ 'nav-open': mobileNav }">
    <aside class="sidebar">
      <RouterLink class="brand-logo" to="/problems"
        ><span class="brand-symbol" aria-hidden="true">{ }</span>Byte Judge</RouterLink
      >
      <div class="sidebar-badge"><span class="admin-badge">ADMIN</span></div>
      <nav class="nav-list" aria-label="主导航">
        <Button variant="ghost" class="nav-item" disabled title="工作台尚未开放"
          ><Home /><span>工作台</span></Button
        >
        <Button variant="ghost" class="nav-item active" as-child
          ><RouterLink to="/problems" aria-current="page" @click="mobileNav = false"
            ><Code2 /><span>题库管理</span></RouterLink
          ></Button
        >
        <Button variant="ghost" class="nav-item" disabled title="比赛管理尚未开放"
          ><Trophy /><span>比赛管理</span></Button
        >
        <Button variant="ghost" class="nav-item" disabled title="提交记录尚未开放"
          ><FileText /><span>提交记录</span></Button
        >
        <Button variant="ghost" class="nav-item" disabled title="用户管理尚未开放"
          ><UsersRound /><span>用户管理</span></Button
        >
        <Button variant="ghost" class="nav-item" disabled title="系统设置尚未开放"
          ><Settings2 /><span>系统设置</span></Button
        >
      </nav>
      <div class="sidebar-profile">
        <span class="avatar"><UserRound :size="20" /></span>
        <div>
          <strong>管理员</strong><small>{{ account }}</small>
        </div>
        <DropdownMenu
          ><DropdownMenuTrigger as-child
            ><Button variant="ghost" size="icon" aria-label="账号菜单" class="account-menu"
              ><ChevronDown :size="16" /></Button></DropdownMenuTrigger
          ><DropdownMenuContent align="end"
            ><DropdownMenuLabel>{{ account }}</DropdownMenuLabel
            ><DropdownMenuSeparator /><DropdownMenuItem @select="logoutOpen = true"
              ><LogOut />退出登录</DropdownMenuItem
            ></DropdownMenuContent
          ></DropdownMenu
        >
      </div>
    </aside>
    <div class="main-column">
      <header class="topbar">
        <Button
          variant="ghost"
          size="icon"
          class="mobile-toggle"
          aria-label="切换导航"
          :aria-expanded="mobileNav"
          @click="mobileNav = !mobileNav"
          ><Menu
        /></Button>
        <div class="breadcrumb">
          <span>内容管理</span><span class="separator">/</span
          ><strong>{{ route.meta.title ?? '题库管理' }}</strong>
        </div>
        <div class="topbar-right">
          <span class="environment">管理控制台</span
          ><DropdownMenu
            ><DropdownMenuTrigger as-child
              ><Button variant="ghost" size="icon" class="top-avatar" aria-label="管理员菜单"
                ><UserRound :size="17" /></Button></DropdownMenuTrigger
            ><DropdownMenuContent align="end"
              ><DropdownMenuLabel>{{ account }}</DropdownMenuLabel
              ><DropdownMenuSeparator /><DropdownMenuItem @select="logoutOpen = true"
                ><LogOut />退出登录</DropdownMenuItem
              ></DropdownMenuContent
            ></DropdownMenu
          >
        </div>
      </header>
      <main class="content"><RouterView /></main>
    </div>
    <AlertDialog v-model:open="logoutOpen"
      ><AlertDialogContent
        ><AlertDialogHeader
          ><AlertDialogTitle>退出登录？</AlertDialogTitle
          ><AlertDialogDescription
            >退出后需要重新登录才能进入管理控制台。</AlertDialogDescription
          ></AlertDialogHeader
        ><AlertDialogFooter
          ><AlertDialogCancel>取消</AlertDialogCancel
          ><AlertDialogAction :disabled="loggingOut" @click="logout">{{
            loggingOut ? '正在退出…' : '退出登录'
          }}</AlertDialogAction></AlertDialogFooter
        ></AlertDialogContent
      ></AlertDialog
    >
  </div>
</template>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 218px minmax(0, 1fr);
  min-height: 100svh;
  background: #fff;
}
.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100svh;
  padding: 27px 14px 20px;
  border-right: 1px solid var(--border);
  background: #fcfbf8;
}
.sidebar .brand-logo {
  gap: 9px;
  padding: 0 8px;
  font-size: 21px;
}
.sidebar .brand-symbol {
  font-size: 30px;
}
.sidebar-badge {
  margin: 7px 0 34px 43px;
}
.nav-list {
  display: grid;
  gap: 9px;
}
.nav-item {
  position: relative;
  justify-content: flex-start;
  gap: 13px;
  width: 100%;
  min-height: 44px;
  padding: 0 17px;
  border-radius: 10px;
  color: #5c626c;
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
}
.nav-item:disabled {
  opacity: 0.62;
}
.nav-item svg {
  width: 18px;
  height: 18px;
}
.nav-item.active {
  overflow: hidden;
  color: var(--primary);
  background: #eff9d7;
  font-weight: 650;
}
.nav-item.active::before {
  position: absolute;
  left: 0;
  top: 9px;
  bottom: 9px;
  width: 3px;
  border-radius: 3px;
  background: var(--primary);
  content: '';
}
.nav-item.active:hover {
  background: #e5f4c1;
}
.sidebar-profile {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: auto;
  padding: 19px 5px 0;
  border-top: 1px solid var(--border);
}
.avatar,
.top-avatar {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e9eeff;
  color: var(--primary);
  box-shadow: inset 0 1px 0 #fff;
}
.sidebar-profile strong {
  display: block;
  font-size: 12px;
  font-weight: 650;
}
.sidebar-profile small {
  display: block;
  max-width: 95px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--muted-text);
  font-size: 10px;
}
.account-menu {
  margin-left: auto;
  width: 27px;
  height: 34px;
}
.main-column {
  min-width: 0;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 62px;
  padding: 0 30px;
  border-bottom: 1px solid var(--border);
  background: #ffffffd9;
  font-size: 11px;
}
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #79808a;
}
.breadcrumb strong {
  color: #333a46;
  font-weight: 500;
}
.separator {
  color: #c2c4bd;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 17px;
}
.environment {
  color: #90958f;
  font-size: 10px;
  letter-spacing: 0.5px;
}
.top-avatar {
  width: 30px;
  height: 30px;
  background: var(--primary);
  color: #fff;
}
.content {
  max-width: 1660px;
  margin: 0 auto;
  padding: 30px;
}
.mobile-toggle {
  display: none;
}
@media (max-width: 1100px) {
  .admin-shell {
    grid-template-columns: 190px minmax(0, 1fr);
  }
  .sidebar .brand-logo {
    font-size: 18px;
  }
  .topbar {
    padding-inline: 22px;
  }
  .content {
    padding: 24px 22px;
  }
}
@media (max-width: 760px) {
  .admin-shell {
    grid-template-columns: 1fr;
  }
  .sidebar {
    display: none;
  }
  .nav-open .sidebar {
    position: fixed;
    z-index: 30;
    display: flex;
    top: 62px;
    bottom: 0;
    width: 218px;
    height: calc(100svh - 62px);
    box-shadow: 15px 0 40px #15191e15;
  }
  .mobile-toggle {
    display: inline-flex;
    margin-right: 8px;
  }
  .topbar {
    padding: 0 15px;
  }
  .breadcrumb {
    margin-right: auto;
  }
  .environment {
    display: none;
  }
  .content {
    padding: 24px 16px;
  }
}
</style>
