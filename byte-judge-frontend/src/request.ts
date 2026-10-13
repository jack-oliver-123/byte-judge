import axios, {
  AxiosError,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";
import { BaseResponse, SUCCESS_CODE } from "@/api/types";
import { BASE_API } from "@/env";

/**
 * 业务错误：HTTP 请求成功（200）但后端 code 非 0。
 *
 * 单独建一个错误类型，是为了让调用方能用 instanceof 区分
 * "业务失败（如未登录、参数错误）" 与 "网络/HTTP 失败（如超时、500）"，
 * 而不是去解析错误字符串。
 */
export class BusinessError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = "BusinessError";
    this.code = code;
  }
}

/**
 * axios 实例。
 *
 * baseURL 不设兜底值：.env.* 是进 git 的，VUE_APP_BASE_API 必然存在，
 * 且 src/env.ts 已在启动时校验过；硬编码 fallback 等于把同一份配置写在两处。
 */
const request = axios.create({
  baseURL: BASE_API,
  timeout: 10000,
  // 后端用 Spring Session + Cookie 鉴权（application.yml 的 spring.session.timeout
  // 配了 30 天 Cookie），跨域请求必须带上 Cookie，否则每次都是新会话、永远未登录。
  // 后端 CORS 已配 allowCredentials(true) + allowedOriginPatterns("*")——
  // 用 patterns 而非 allowedOrigins("*") 是必须的，后者与 allowCredentials 冲突会直接报错。
  withCredentials: true,
});

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // ⬇⬇ 扩展点：若将来后端改用 JWT，在此注入请求头，并把上面的 withCredentials 去掉。
    // const token = localStorage.getItem("token");
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  (error: AxiosError) => Promise.reject(error)
);

request.interceptors.response.use(
  // 刻意不改变返回值结构（不 return response.data）。
  // 一旦在此解包，运行时值就与 axios 的 AxiosResponse<T> 类型不符，
  // 只能靠 declare module "axios" 去骗类型系统——在 strict 模式下是纯负债。
  // BaseResponse 的解包交给下方 unwrap()，全程类型可信。
  (response: AxiosResponse) => response,
  (error: AxiosError) => {
    // ⚠️ 这里收不到"登录态失效"。该后端的 GlobalExceptionHandler 把 BusinessException
    // 统一转成 HTTP 200 + code 40100（未登录）/ 40101（无权限），走的是下面 unwrap()
    // 抛 BusinessError 的路径，不会进入本分支。本分支只处理真正的传输层失败。
    //
    // 若要集中处理登录态失效，扩展点在 unwrap() 里按 code 判断，而非在此判断 status。

    // 只做错误信息归一化，不在此弹提示：
    // getLoginUser 这类"探测型"请求失败是正常情况，无脑弹错会让每次进页面都跳红条。
    // 要不要提示用户由调用方决定。
    error.message = resolveErrorMessage(error);
    return Promise.reject(error);
  }
);

/**
 * 把 axios 的各类失败归一化成一句可读的中文提示
 */
function resolveErrorMessage(error: AxiosError): string {
  if (error.code === AxiosError.ECONNABORTED) {
    return "请求超时，请稍后重试";
  }
  if (!error.response) {
    return "网络异常，请检查网络或后端服务是否已启动";
  }
  const { status } = error.response;
  if (status >= 500) {
    return `服务端异常（${status}）`;
  }
  return `请求失败（${status}）`;
}

/**
 * 解包后端统一响应。
 *
 * 业务是否成功看 code 而非 HTTP 状态码——后端业务失败时通常仍返回 200。
 * 抽成泛型函数而不是在每个接口函数里重复写 if，既保持 DRY 又不丢类型。
 *
 * @throws {BusinessError} code 非 0 时抛出
 */
export function unwrap<T>(response: AxiosResponse<BaseResponse<T>>): T {
  const { code, data, message } = response.data;
  if (code !== SUCCESS_CODE) {
    throw new BusinessError(code, message ?? "请求失败");
  }
  return data;
}

export default request;
