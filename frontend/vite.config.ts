/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// In development the proxy makes /api same-origin, so the browser needs no CORS for it
// and the HttpOnly refresh cookie (path /api/v1/auth) is sent normally.
export default defineConfig({
    plugins: [react(), tailwindcss()],
    server: {
        port: 5173,
        proxy: {
            '/api': { target: 'http://localhost:8080', changeOrigin: true },
        },
    },
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/test/setup.ts'],
        css: false,
    },
})