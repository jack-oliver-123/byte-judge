import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AdminLayout from '@/layouts/AdminLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/problems',
      children: [
        {
          path: 'problems/:id/edit',
          name: 'problem-edit',
          component: () => import('@/views/ProblemEditView.vue'),
          meta: { requiresAdmin: true, title: '编辑题目草稿' },
        },
        {
          path: 'problems',
          name: 'problems',
          component: () => import('@/views/ProblemsView.vue'),
          meta: { requiresAdmin: true, title: '题库管理' },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.ready) await auth.restore()
  if (to.meta.requiresAdmin && !auth.identity) {
    return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
  }
  return true
})

export default router
