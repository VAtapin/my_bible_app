import { createRouter, createWebHistory } from 'vue-router'
import { azbukaRoutes } from './routes'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    ...azbukaRoutes,
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior: (to,from,saved) => saved ?? (to.path==='/dictionary' && from.path===to.path &&
    ['q','mode','letter'].every(key=>to.query[key]===from.query[key]) ? false : { top: 0 })
})
