import js from '@eslint/js'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'

export default tseslint.config(
  { ignores: ['dist/**', 'node_modules/**', 'playwright-report/**', 'test-results/**', 'src/auto-imports.d.ts', 'src/components.d.ts'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.{ts,vue}'],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
      parserOptions: { parser: tseslint.parser },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
  {
    files: ['**/features/playground/**'],
    rules: {
      // Playground renders assistant output through renderSafeMarkdown, which
      // escapes raw HTML and filters non-http(s) link schemes before use.
      'vue/no-v-html': 'off',
    },
  },
  {
    files: ['**/*.test.ts'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
  },
)
