/* 本文件不是生成产物：它经 scripts/gen-api.js 的 --request 覆盖
 * src/generated/core/request.ts，所以写在 scripts/ 下、不会被重新生成覆盖。
 *
 * 生成的 Service 一律这样调用（签名由生成器固定，不能改）：
 *   import { request as __request } from '../core/request';
 *   return __request(OpenAPI, { method: 'GET', url: '/user/get/login' });
 *
 * 这里把请求转发给 src/request.ts 的 axios 实例，从而复用它的
 * baseURL、超时、withCredentials 和错误信息归一化，而不是让生成代码
 * 自带第二份 axios 配置。
 */
import type { AxiosResponse } from "axios";
import http, { unwrap } from "@/request";
import type { BaseResponse } from "@/api/types";
import type { ApiRequestOptions } from "./ApiRequestOptions";
import { CancelablePromise } from "./CancelablePromise";
import type { OpenAPIConfig } from "./OpenAPI";

/**
 * 生成代码的唯一请求入口。
 *
 * 返回解包后的 data：后端业务失败仍是 HTTP 200，成功与否由 unwrap()
 * 按 code 判断，失败抛 BusinessError——与手写的 src/api 行为一致。
 *
 * 由此生成函数的声明返回类型（BaseResponse<T>）比运行时实际值（T）宽一层，
 * 这是刻意的：解包逻辑收敛在这一处，而不是散落到每个调用方各自判断 code。
 * 前提是所有经这里调用的接口都返回 BaseResponse（微信回调不走前端，不受影响）。
 */
export const request = <T>(
  _config: OpenAPIConfig,
  options: ApiRequestOptions
): CancelablePromise<T> => {
  return new CancelablePromise((resolve, reject) => {
    send<T>(options).then(resolve, reject);
  });
};

/**
 * 把生成器的请求描述翻译成 axios 请求。
 *
 * query 与 axios 的 params 同义；mediaType 决定 body 放 data 还是
 * 作为表单字段展开。formData 与 body 在 OpenAPI 里互斥，取其一。
 */
function send<T>(options: ApiRequestOptions): Promise<T> {
  const formData = options.formData;
  const isMultipart = options.mediaType === "multipart/form-data";

  return http
    .request<BaseResponse<T>>({
      method: options.method,
      url: options.url,
      params: options.query,
      data: isMultipart ? toFormData(formData) : options.body ?? formData,
      responseType: options.responseHeader ? "text" : "json",
    })
    .then((response: AxiosResponse<BaseResponse<T>>) => unwrap(response));
}

/**
 * 仅在确有文件上传时才组装 FormData。
 * 值为 undefined 的字段跳过，与 axios 序列化 JSON 时丢弃 undefined 的行为对齐。
 */
function toFormData(
  fields: Record<string, unknown> | undefined
): FormData | undefined {
  if (!fields) {
    return undefined;
  }
  const data = new FormData();
  for (const [key, value] of Object.entries(fields)) {
    if (value === undefined) {
      continue;
    }
    data.append(
      key,
      value instanceof Blob || typeof value === "string" ? value : String(value)
    );
  }
  return data;
}
