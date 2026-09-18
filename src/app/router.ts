import { createRouter, createWebHistory } from 'vue-router'
import PrototypeView from '@/features/prototype/PrototypeView.vue'
import WelcomeView from '@/features/onboarding/WelcomeView.vue'
import SetupView from '@/features/onboarding/SetupView.vue'
import RestoreView from '@/features/onboarding/RestoreView.vue'
import TodayView from '@/features/today/TodayView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'welcome',
      component: WelcomeView,
    },
    {
      path: '/setup/quick',
      name: 'setup-quick',
      component: SetupView,
      props: { mode: 'quick' },
    },
    {
      path: '/setup/manual',
      name: 'setup-manual',
      component: SetupView,
      props: { mode: 'manual' },
    },
    {
      path: '/restore',
      name: 'restore',
      component: RestoreView,
    },
    {
      path: '/today',
      name: 'today',
      component: TodayView,
    },
    {
      path: '/reader',
      name: 'reader',
      component: PrototypeView,
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})
