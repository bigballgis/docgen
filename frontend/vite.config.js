import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:3001',
        changeOrigin: true
      }
    }
  },
  build: {
    // 代码分割
    rollupOptions: {
      output: {
        manualChunks(id) {
          // Element Plus 按模块拆分
          if (id.includes('element-plus')) {
            // 将 Element Plus 拆分为更小的 chunk
            if (id.includes('@element-plus/icons-vue')) {
              return 'element-icons'
            }
            return 'element-plus'
          }
          // 第三方核心库
          if (id.includes('node_modules') && !id.includes('element-plus')) {
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router') || id.includes('axios')) {
              return 'vendor'
            }
            if (id.includes('vue-i18n') || id.includes('@intlify')) {
              return 'i18n'
            }
            if (id.includes('dompurify')) {
              return 'sanitize'
            }
            return 'vendor'
          }
        }
      }
    },
    // 启用 gzip 压缩
    cssCodeSplit: true,
    // 生成 source map
    sourcemap: false,
    // 构建输出目录
    outDir: 'dist',
    // 静态资源目录
    assetsDir: 'assets',
    // 缓存策略
    assetsInlineLimit: 4096 // 4kb 以下的资源内联
  }
})
