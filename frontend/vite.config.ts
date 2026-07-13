import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'pinia', 'vue-router'],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts'
    })
  ],
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
