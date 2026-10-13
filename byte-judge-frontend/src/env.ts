/**
 * 环境变量的唯一收口处。
 *
 * 为什么要有这个文件：
 * tsconfig 的 types 只含 "webpack-env"，而它对 process 的声明是
 * `interface NodeProcess { env?: any }`——env 是 any，TS 在此处提供零保护。
 * 把这个 any 边界收敛到本文件一处，对外只导出带类型的常量，
 * 业务代码一律 import 这里的常量，不要再直接写 process.env.XXX。
 *
 * 下面的校验不是"兜底"，而是 fail-fast：
 * webpack 的 DefinePlugin 是编译期文本替换，变量缺失时会被替换成 undefined，
 * 而 TS 因为类型是 any 不会报错。没有这段校验，配错的后果是请求静默打到相对路径。
 */

const baseApi: unknown = process.env.VUE_APP_BASE_API;

if (typeof baseApi !== "string" || baseApi.length === 0) {
  throw new Error(
    "缺少环境变量 VUE_APP_BASE_API，请检查 .env.development / .env.production"
  );
}

/**
 * 后端接口 base URL
 */
export const BASE_API: string = baseApi;
