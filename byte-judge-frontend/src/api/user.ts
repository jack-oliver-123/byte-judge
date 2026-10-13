import request, { unwrap } from "@/request";
import { BaseResponse, LoginUserVO } from "./types";

/**
 * 登录请求体，与后端 UserLoginRequest 对齐。
 */
export interface UserLoginRequest {
  userAccount: string;
  userPassword: string;
}

/**
 * 注册请求体，与后端 UserRegisterRequest 对齐。
 */
export interface UserRegisterRequest {
  userAccount: string;
  userPassword: string;
  checkPassword: string;
}

/**
 * 用户登录。
 *
 * 成功时后端把登录态写入 Session（靠 Set-Cookie 下发），返回值只是用户 id，
 * 不含角色信息。要拿到角色得再调 getLoginUserApi。
 */
export async function userLoginApi(
  userLoginRequest: UserLoginRequest
): Promise<number> {
  return unwrap(
    await request.post<BaseResponse<number>>("/user/login", userLoginRequest)
  );
}

/**
 * 用户注册。
 *
 * 成功只返回新用户 id，不建立登录态——注册完仍需走登录接口。
 */
export async function userRegisterApi(
  userRegisterRequest: UserRegisterRequest
): Promise<number> {
  return unwrap(
    await request.post<BaseResponse<number>>(
      "/user/register",
      userRegisterRequest
    )
  );
}

/**
 * 获取当前登录用户。
 *
 * 该接口是"探测型"请求：未登录时后端返回业务错误码（而非 HTTP 401），
 * 调用方应 catch 后视作未登录处理，不要向用户弹错。
 * 见 src/store/user.ts 的 getLoginUser。
 */
export async function getLoginUserApi(): Promise<LoginUserVO> {
  return unwrap(
    await request.get<BaseResponse<LoginUserVO>>("/user/get/login")
  );
}
