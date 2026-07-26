import { createRouter, createWebHistory } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('./views/HomeView.vue') },
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue') },
    { path: '/tasks', name: 'tasks', component: () => import('./views/TaskListView.vue') },
    { path: '/tasks/new', name: 'task-new', component: () => import('./views/TaskEditView.vue') },
    {
      path: '/tasks/:id',
      name: 'task-detail',
      component: () => import('./views/TaskDetailView.vue'),
      props: true,
    },
    {
      path: '/tasks/:id/edit',
      name: 'task-edit',
      component: () => import('./views/TaskEditView.vue'),
      props: true,
    },
    { path: '/projects', name: 'projects', component: () => import('./views/ProjectsView.vue') },
    // Claude Code ハンドオフの中継ページ(ユニバーサルリンク回避)
    { path: '/handoff', name: 'handoff', component: () => import('./views/HandoffView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
