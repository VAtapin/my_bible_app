import { createRouter, createWebHistory } from 'vue-router'
import PrototypeView from '@/features/prototype/PrototypeView.vue'
import WelcomeView from '@/features/onboarding/WelcomeView.vue'
import SetupView from '@/features/onboarding/SetupView.vue'
import RestoreView from '@/features/onboarding/RestoreView.vue'
import TodayView from '@/features/today/TodayView.vue'
import StorageView from '@/features/storage/StorageView.vue'
import PrayerListView from '@/features/prayers/PrayerListView.vue'
import PrayerView from '@/features/prayers/PrayerView.vue'
import CalendarView from '@/features/calendar/CalendarView.vue'
import NotificationSettingsView from '@/features/notifications/NotificationSettingsView.vue'
import ProfileSettingsView from '@/features/profile/ProfileSettingsView.vue'

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
      path: '/storage',
      name: 'storage',
      component: StorageView,
    },
    {
      path: '/prayers',
      name: 'prayers',
      component: PrayerListView,
    },
    {
      path: '/prayers/:id',
      name: 'prayer',
      component: PrayerView,
    },
    {
      path: '/calendar',
      name: 'calendar',
      component: CalendarView,
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: NotificationSettingsView,
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfileSettingsView,
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})
