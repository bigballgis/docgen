import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const isCollapsed = ref(localStorage.getItem('sidebar-collapsed') === 'true')

  function toggleSidebar() {
    isCollapsed.value = !isCollapsed.value
    localStorage.setItem('sidebar-collapsed', String(isCollapsed.value))
  }

  return { isCollapsed, toggleSidebar }
})
