import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/avatar': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/edu': {
        target: 'https://jwxt.aqnu.edu.cn',
        changeOrigin: true,
        secure: false,
        rewrite: (path) => path.replace(/^\/edu/, '/student')
      },
      '/student': {
        target: 'https://jwxt.aqnu.edu.cn',
        changeOrigin: true,
        secure: false
      }
    }
  }
})
