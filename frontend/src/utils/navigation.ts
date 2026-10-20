import router from '@/router'

/**
 * 返回上一页。
 *
 * 直接用 router.back() 的问题：未登录访问受保护页时，路由守卫会先跳到 /login，
 * 登录成功后再跳回原页面，于是历史栈变成 `… → /login → 当前页`，
 * 此时"返回"会把已登录的用户送回登录页。
 *
 * vue-router 4 每次导航都会把上一页的 fullPath 写进 history.state.back，
 * 这里据此判断：上一页是登录/注册页（或压根没有上一页）时改回主页，
 * 其余情况保持正常的后退行为。
 *
 * 注意 history.state.back 存的是 fullPath，带 query 和 hash——
 * 守卫跳转产生的就是 `/login?redirect=/xxx`，所以必须比较路径部分，
 * 不能直接和 '/login' 相等比较，否则会漏判并退回到登录页。
 */
export const goBack = () => {
  const prevPath = (window.history.state?.back as string | undefined)?.split(/[?#]/)[0]
  if (!prevPath || prevPath === '/login' || prevPath === '/register') {
    router.replace('/home')
    return
  }
  router.back()
}
