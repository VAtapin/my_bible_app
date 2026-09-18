import { createRouter, createWebHistory } from 'vue-router'
import PrototypeView from '@/features/prototype/PrototypeView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'prototype',
      component: PrototypeView,
    },
  ],
})
