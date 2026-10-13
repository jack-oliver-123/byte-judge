import request, { unwrap } from "@/request";
import { BaseResponse, LoginUserVO } from "./types";

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
