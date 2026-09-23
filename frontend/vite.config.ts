import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In development the Vite server proxies /api to the Spring Boot backend, so the browser
// talks to a single origin and no CORS round-trips are needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
        // Proxied calls are same-origin from the browser's point of view, so drop the Origin
        // header; otherwise the backend's CORS check would reject any dev port it doesn't list.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        },
      },
    },
  },
})
