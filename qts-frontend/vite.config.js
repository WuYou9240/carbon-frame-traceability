import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 构建产物直接输出到后端 static 目录：
// 打包后 Spring Boot 会在同一个端口托管前端，部署只需要一个 jar，
// 不必再单独开 npm dev server（答辩现场少一个出错环节）。
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: '../qts-backend/src/main/resources/static',
    emptyOutDir: true
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:18080',
        changeOrigin: true
      }
    }
  }
})
