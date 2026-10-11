import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server runs on http://localhost:5173.
// Any request starting with /api is forwarded to Spring Boot on port 8080,
// so the React code can simply call fetch('/api/...') with no CORS setup.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
