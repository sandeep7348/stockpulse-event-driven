import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      strictPort: true,
      // Same-origin requests in development, so the backend needs no CORS setup.
      proxy: { '/api': env.API_PROXY_TARGET ?? 'http://localhost:8090' },
    },
  };
});
