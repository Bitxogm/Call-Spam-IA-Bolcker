import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    // BackendSyncService las lee al cargar el módulo
    env: {
      EXPO_PUBLIC_VPS_URL: 'https://test.invalid',
      EXPO_PUBLIC_CONTROL_API_TOKEN: 'token-de-test',
    },
  },
});
