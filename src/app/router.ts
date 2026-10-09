import { createRouter, createWebHistory } from 'vue-router'
import PrototypeView from '@/features/prototype/PrototypeView.vue'
import BibleSearchView from '@/features/prototype/BibleSearchView.vue'
import BibleLibraryView from '@/features/prototype/BibleLibraryView.vue'
import WelcomeView from '@/features/onboarding/WelcomeView.vue'
import SetupView from '@/features/onboarding/SetupView.vue'
import RestoreView from '@/features/onboarding/RestoreView.vue'
import TodayView from '@/features/today/TodayView.vue'
import StorageView from '@/features/storage/StorageView.vue'
import PrayerListView from '@/features/prayers/PrayerListView.vue'
import PrayerView from '@/features/prayers/PrayerView.vue'
import LiturgicalWorkView from '@/features/prayers/LiturgicalWorkView.vue'
import CalendarView from '@/features/calendar/CalendarView.vue'
import NotificationSettingsView from '@/features/notifications/NotificationSettingsView.vue'
import ProfileSettingsView from '@/features/profile/ProfileSettingsView.vue'
import PrivacyView from '@/features/legal/PrivacyView.vue'
import DiagnosticsView from '@/features/diagnostics/DiagnosticsView.vue'
import EducationView from '@/features/education/EducationView.vue'
import MoreView from '@/features/profile/MoreView.vue'
import { interfaceLanguageIds } from '@/i18n/locale'
import { configureAzbukaIntegration } from '../../azbuka-web/src/integration'
import { azbukaRoutes } from '../../azbuka-web/src/routes'

configureAzbukaIntegration('/education/azbuka')

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/books', component: () => import('@/features/study/BooksView.vue') },
    { path: '/books/:id', component: () => import('@/features/study/StudyBookView.vue') },
    { path: '/bibles', name: 'bible-library', component: BibleLibraryView },
    { path: '/search', name: 'bible-search', component: BibleSearchView },
    {
      path: '/',
      name: 'welcome',
      component: WelcomeView,
    },
    ...interfaceLanguageIds.map((code) => ({ path: `/${code}`, name: `welcome-${code}`, component: WelcomeView })),
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
      path: '/liturgical/:slug/:language',
      name: 'liturgical-work',
      component: LiturgicalWorkView,
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
      path: '/privacy',
      name: 'privacy',
      component: PrivacyView,
    },
    {
      path: '/diagnostics',
      name: 'diagnostics',
      component: DiagnosticsView,
    },
    { path: '/more', name: 'more', component: MoreView },
    {
      path: '/education',
      name: 'education',
      component: EducationView,
    },
    {
      path: '/education/azbuka',
      component: () => import('@/features/education/AzbukaLayout.vue'),
      children: azbukaRoutes.map((route) => ({ ...route, path: route.path.replace(/^\//, ''), name: `azbuka-${String(route.name)}` })),
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})
