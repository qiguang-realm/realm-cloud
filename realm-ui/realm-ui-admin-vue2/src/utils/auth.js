import Cookies from 'js-cookie'

const AccessTokenKey = 'Access-Token'
const RefreshTokenKey = 'Refresh-Token'

/**
 * 读取 Access Token。
 *
 * 被 src/utils/request.js 的请求拦截器调用，每个请求都会读取一次，
 * 拼到 Authorization: Bearer <token> 头里。
 */
export function getAccessToken() {
  return Cookies.get(AccessTokenKey)
}

/**
 * 写入 Access Token。
 *
 * 被 src/store/modules/user.js 的 login / refreshToken action 调用。
 */
export function setAccessToken(token) {
  return Cookies.set(AccessTokenKey, token)
}

/**
 * 读取 Refresh Token。
 *
 * 被 src/store/modules/user.js 的 refreshToken / logout action 调用。
 */
export function getRefreshToken() {
  return Cookies.get(RefreshTokenKey)
}

/**
 * 写入 Refresh Token。
 *
 * 被 src/store/modules/user.js 的 login / refreshToken action 调用。
 * 后端实现了 Refresh Token 轮换（rotation），每次刷新都会返回新的
 * refreshToken，前端必须用新值覆盖，不能只更新 accessToken。
 */
export function setRefreshToken(token) {
  return Cookies.set(RefreshTokenKey, token)
}

/**
 * 清除所有 token。
 *
 * 被 src/store/modules/user.js 的 logout / resetToken action 调用，
 * 以及 src/utils/request.js 在刷新失败时调用。
 */
export function removeToken() {
  Cookies.remove(AccessTokenKey)
  Cookies.remove(RefreshTokenKey)
}
