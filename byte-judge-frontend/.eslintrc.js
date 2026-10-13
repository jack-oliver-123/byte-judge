module.exports = {
  root: true,
  env: {
    node: true,
  },
  extends: [
    "plugin:vue/vue3-essential",
    "eslint:recommended",
    "@vue/typescript/recommended",
    "plugin:prettier/recommended",
  ],
  parserOptions: {
    ecmaVersion: 2020,
  },
  rules: {
    "no-console": process.env.NODE_ENV === "production" ? "warn" : "off",
    "no-debugger": process.env.NODE_ENV === "production" ? "warn" : "off",
  },
  // src/generated 由 npm run gen:api 生成，每次都是整目录覆盖。
  // 不忽略的话 `lint --fix` 会改写生成文件，下一次生成又把格式改回去，来回抖动。
  ignorePatterns: ["src/generated/"],
};
