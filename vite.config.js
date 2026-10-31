import { defineConfig } from 'vite';

// Relative base so the same build works on GitHub Pages under /<repo>/.
export default defineConfig({
  base: './',
  build: {
    outDir: 'dist',
    target: 'es2020',
  },
});
