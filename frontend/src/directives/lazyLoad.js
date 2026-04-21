import { useIntersectionObserver } from '@vueuse/core'

/**
 * 图片懒加载指令
 * 使用方法: <img v-lazy-load="imageSrc" alt="description" />
 */
export const lazyLoad = {
  mounted(el, binding) {
    const { stop } = useIntersectionObserver(
      el,
      ([{ isIntersecting }]) => {
        if (isIntersecting) {
          el.src = binding.value
          stop()
        }
      },
      {
        threshold: 0.1
      }
    )
  }
}

export default {
  install(app) {
    app.directive('lazy-load', lazyLoad)
  }
}