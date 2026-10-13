/**
 * 权限角色常量。
 *
 * 取值刻意用字符串而非数字，是为了与后端 user 表的 userRole 字段直接对齐
 * （后端通常返回 "user" / "admin"），避免中间再做一层映射。
 *
 * 用 `as const` 对象 + 派生联合类型而非 TS enum：enum 会产出额外的运行时代码，
 * 且与字符串字面量互相转换时类型推导别扭；as const 在 strict 模式下推导更精确。
 */
export const ACCESS_ENUM = {
  /**
   * 未登录。
   *
   * 注意这不是后端的角色取值（后端 UserRoleEnum 只有 user / admin / ban），
   * 而是前端补的一个状态，同时充当"公开页面"的 needAccess 取值。
   */
  NOT_LOGIN: "notLogin",
  /** 普通用户，对应后端 UserRoleEnum.USER */
  USER: "user",
  /** 管理员，对应后端 UserRoleEnum.ADMIN */
  ADMIN: "admin",
  /** 已封号，对应后端 UserRoleEnum.BAN */
  BAN: "ban",
} as const;

/**
 * 权限角色的联合类型："notLogin" | "user" | "admin" | "ban"
 */
export type AccessEnum = (typeof ACCESS_ENUM)[keyof typeof ACCESS_ENUM];

/**
 * 权限等级，数值越大权限越高。
 *
 * 仅服务于 checkAccess 的比较逻辑，不要在业务代码里直接引用这些数字——
 * 业务侧一律用 ACCESS_ENUM 常量。
 */
export const ACCESS_RANK: Record<AccessEnum, number> = {
  [ACCESS_ENUM.NOT_LOGIN]: 0,
  // 封号用户与未登录同级：公开页面仍可看，其余一律拦。
  // 之所以不给负数，是因为 needAccess 永远不会是 BAN，比较时它只出现在左侧。
  [ACCESS_ENUM.BAN]: 0,
  [ACCESS_ENUM.USER]: 1,
  [ACCESS_ENUM.ADMIN]: 2,
};

/**
 * 判断一个任意字符串是否是合法的权限角色。
 *
 * 后端返回的 userRole 在类型上不可信（可能是新增的、或拼错的角色），
 * 入口处收窄一次，避免 ACCESS_RANK 取值取出 undefined 后参与数值比较得到 NaN。
 */
export function isAccessEnum(value: unknown): value is AccessEnum {
  return typeof value === "string" && value in ACCESS_RANK;
}
