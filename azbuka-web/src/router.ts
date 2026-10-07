import { createRouter, createWebHistory } from 'vue-router'
import { azbukaRoutes } from './routes'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    ...azbukaRoutes,
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior: () => ({ top: 0 })
})
