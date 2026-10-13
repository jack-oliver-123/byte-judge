/**
 * 后端统一响应包装。
 *
 * 与后端 BaseResponse<T> 对齐：业务是否成功看 code，而非 HTTP 状态码——
 * 后端在业务失败时通常仍返回 HTTP 200，只是 code 非 0。
 */
export interface BaseResponse<T> {
  code: number;
  data: T;
  message?: string;
}

/**
 * 业务成功的 code 取值。与后端 ErrorCode.SUCCESS(0, "ok") 对齐。
 */
export const SUCCESS_CODE = 0;

/**
 * 后端返回的登录用户视图对象，与后端 LoginUserVO 字段逐一对齐。
 *
 * 独立于 store 的 LoginUser：这里是"接口契约"，字段名由后端决定；
 * LoginUser 是"前端状态"，命名由前端自己定。
 * 两者当前几乎同形，但不应合并——后端加字段时不该被迫改动前端状态模型。
 *
 * ⚠️ 字段名是 userName（大写 N），不是 username。
 * 后端 Java 字段为 `private String userName`，Jackson 序列化后 JSON key 即 userName。
 * 写错不会有任何编译报错（类型里写什么就是什么），只会在运行时取到 undefined，
 * 表现为导航栏用户名空白——改这个接口时务必对着后端 VO 核字段名。
 */
export interface LoginUserVO {
  id?: number;
  userName: string;
  userRole: string;
  userAvatar?: string;
}
