import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import AlphabetView from '@/views/AlphabetView.vue'
import LetterView from '@/views/LetterView.vue'
import PracticeView from '@/views/PracticeView.vue'
import PracticeSessionView from '@/views/PracticeSessionView.vue'
import ProfileView from '@/views/ProfileView.vue'
import OnboardingView from '@/views/OnboardingView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/alphabet', name: 'alphabet', component: AlphabetView },
    { path: '/alphabet/:id', name: 'letter', component: LetterView },
    { path: '/practice', name: 'practice', component: PracticeView },
    { path: '/practice/session', name: 'practice-session', component: PracticeSessionView },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/onboarding', name: 'onboarding', component: OnboardingView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior: () => ({ top: 0 })
})
