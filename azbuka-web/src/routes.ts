import type { RouteRecordRaw } from 'vue-router'
import HomeView from './views/HomeView.vue'
import AlphabetView from './views/AlphabetView.vue'
import LetterView from './views/LetterView.vue'
import PracticeView from './views/PracticeView.vue'
import PracticeSessionView from './views/PracticeSessionView.vue'
import ProfileView from './views/ProfileView.vue'
import OnboardingView from './views/OnboardingView.vue'
import NumbersView from './views/NumbersView.vue'

export const azbukaRoutes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/alphabet', name: 'alphabet', component: AlphabetView },
  { path: '/alphabet/:id', name: 'letter', component: LetterView },
  { path: '/numbers', name: 'numbers', component: NumbersView },
  { path: '/practice', name: 'practice', component: PracticeView },
  { path: '/practice/session', name: 'practice-session', component: PracticeSessionView },
  { path: '/profile', name: 'profile', component: ProfileView },
  { path: '/onboarding', name: 'onboarding', component: OnboardingView },
]
