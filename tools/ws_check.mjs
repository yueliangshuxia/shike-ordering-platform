/* 验证「下单 → 后厨屏收到推送」这条链路真的通。
 *
 * 这一条用 HTTP 测不出来：推送是服务端主动发的，
 * 只有真的挂一个 WebSocket 连接上去、下单、等消息，才能证明它在工作。
 *
 * 用 Node 内置的 WebSocket（Node 22+ 自带，不用装 ws 包）。
 *
 * 用法（后端要起着）：
 *     node tools/ws_check.mjs
 */
const API = 'http://localhost:8080'
const WS = 'ws://localhost:8080/ws/kitchen'

const log = (...a) => console.log(...a)

async function api(method, path, body, token) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json()
  if (json.code !== 0) throw new Error(`${path} -> ${json.code} ${json.message}`)
  return json.data
}

const login = (u, p) => api('POST', '/api/auth/login', { username: u, password: p })
const tokenOf = async (u, p) => (await login(u, p)).token

let received = 0
let orderNo = null

const ws = new WebSocket(WS)

const timeout = setTimeout(() => {
  log(`[!! ] 超时：${received} 条推送`)
  log('      下单没有触发后厨推送。检查 KitchenBroadcaster 是否收到事件、')
  log('      SecurityConfig 有没有放行 /ws/**。')
  process.exit(1)
}, 20000)

ws.addEventListener('open', async () => {
  log('[ok ] WebSocket 连上了')

  try {
    const token = await tokenOf('13800000621', '123456')

    // 挑一道有货的菜
    const dishes = await api('GET', '/api/dishes?includeSoldOut=false')
    const pick = dishes.find((d) => !d.soldOut && d.stock > 0)
    if (!pick) throw new Error('菜单里没有可下单的菜')

    const order = await api(
      'POST',
      '/api/orders',
      {
        dineMode: 'DINE_IN',
        tableCode: 'A12',
        payMethod: '微信支付',
        remark: '推送自测',
        items: [{ dishCode: pick.code, qty: 1 }],
      },
      token,
    )
    orderNo = order.orderNo
    log(`[ok ] 下了一单 ${order.orderNo}（${pick.name}）`)
  } catch (e) {
    clearTimeout(timeout)
    log('[!! ] 下单失败：', e.message)
    process.exit(1)
  }
})

ws.addEventListener('message', (ev) => {
  received++
  const payload = JSON.parse(ev.data)
  log(`[ok ] 收到推送 #${received}：type=${payload.type} orderNo=${payload.orderNo}`)

  if (payload.orderNo === orderNo) {
    const found = (payload.orders || []).some((o) => o.orderNo === orderNo)
    if (!found) {
      log('[!! ] 推送里没有刚下的那一单，后厨屏刷新也看不到它')
      clearTimeout(timeout)
      process.exit(1)
    }
    log(`[ok ] 推送内容里带上了这一单，后厨屏会显示它`)
    log(`      当前队列 ${payload.orders.length} 单，后厨端在线 ${payload.online} 个`)

    // 收尾：把测试单取消掉，免得留在后厨屏上
    clearTimeout(timeout)
    ws.close()
    log('\n推送链路正常。')
    process.exit(0)
  }
})

ws.addEventListener('error', () => {
  clearTimeout(timeout)
  log('[!! ] WebSocket 连接出错。后端起了吗？端口是 8080 吗？')
  process.exit(1)
})
