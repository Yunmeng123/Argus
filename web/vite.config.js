import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发时代理到本地后端
      '/api': { target: 'http://localhost:18080', changeOrigin: true }
    }
  },
  build: {
    // 构建产物直接进 Spring Boot static/, 与后端同源部署
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})
