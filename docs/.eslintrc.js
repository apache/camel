module.exports = {
  extends: 'standard',
  rules: {
    'arrow-parens': ['error', 'always'],
    'comma-dangle': [
      'error',
      {
        arrays: 'always-multiline',
        objects: 'always-multiline',
        imports: 'always-multiline',
        exports: 'always-multiline',
      },
    ],
    'max-len': [
      'error',
      {
        code: 120,
        ignoreStrings: true,
        ignoreUrls: true,
        ignoreTemplateLiterals: true,
      },
    ],
    'spaced-comment': 'off',
    eqeqeq: ['error', 'always'],
    semi: ['error', 'never'],
  },
}
