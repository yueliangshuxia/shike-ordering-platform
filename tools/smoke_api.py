# -*- coding: utf-8 -*-
"""后端接口的端到端自测。

跟 `_verify.py`（测静态 Demo 的逻辑）和 `mvn test`（测算法律）不同，
这个脚本打的是<b>真的跑起来的后端</b>：真 MySQL、真 JWT、真状态机。
它回答的是"整条链路串起来还对不对"，而前两者回答不了这个问题。

跑之前先把后端起起来（`bash tools/run_server.sh`），然后：

    PYTHONIOENCODING=utf-8 python tools/smoke_api.py

覆盖的链路：
    逛菜单 → 游客推荐 → 注册（冷启动）→ 登录 → 存口味 → 改口味看推荐变化
    → 下单（堂食 / 自取两种计费）→ 库存真的扣了 → 取消退回库存
    → 后厨收单并流转 → 非法流转被挡 → 后台看板 → 越权访问被挡

最后一项特别重要：权限如果只在"能访问"的方向上测过，
漏掉一条放行规则是发现不了的。
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080"

# 控制台是 GBK，不设这个 print 中文会炸
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

passed = 0
failed = 0


def call(method, path, body=None, token=None, expect=0):
    """发一个请求，返回 (http_status, 响应体里的 data)。"""
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            status = resp.status
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        status = e.code
        try:
            payload = json.loads(e.read().decode("utf-8"))
        except Exception:
            payload = {"code": -1, "message": "响应不是 JSON"}

    if expect is not None and payload.get("code") != expect:
        raise AssertionError(
            f"{method} {path} 期望 code={expect}，实际 {payload.get('code')} "
            f"({payload.get('message')})")
    return status, payload.get("data"), payload


def check(label, cond, detail=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  [ok ] {label}")
    else:
        failed += 1
        print(f"  [!! ] {label}   {detail}")


def section(title):
    print(f"\n---------- {title} ----------")


def login(username, password):
    _, data, _ = call("POST", "/api/auth/login",
                      {"username": username, "password": password})
    return data["token"]


# =====================================================================
section("健康检查")
_, health, _ = call("GET", "/api/health")
check("服务在跑", health["status"] == "UP")
check("数据库连得上", health["database"] == "UP", health["database"])
check("Redis 没装也能起（走了降级）", "ConcurrentMap" in health["cache"], health["cache"])
check("AHP 一致性检验通过 CR=0.0188", abs(health["ahpCR"] - 0.0188) < 1e-6, health["ahpCR"])

# =====================================================================
section("菜单")
_, cats, _ = call("GET", "/api/categories")
check("第一个分类是虚拟的「招牌推荐」",
      cats[0]["code"] == "signature" and cats[0]["virtual"], cats[0])
check("招牌推荐有菜", cats[0]["count"] > 0, cats[0]["count"])

_, dishes, _ = call("GET", "/api/dishes")
check("菜品列表非空", len(dishes) > 0, len(dishes))
check("售罄的菜也返回了（菜单页要置灰展示）",
      any(d["soldOut"] for d in dishes))
check("每道菜都带分类 code 和标签",
      all(d["categoryId"] and isinstance(d["tags"], list) for d in dishes))

_, signature, _ = call("GET", "/api/dishes?category=signature")
check("虚拟分类「招牌推荐」能筛出菜", len(signature) > 0, len(signature))
check("筛出来的都是招牌", all("招牌" in d["tags"] for d in signature))

avail = [d for d in dishes if not d["soldOut"]]
pick_a, pick_b = avail[0], avail[1]

# =====================================================================
section("推荐：游客（冷启动）")
_, rec, _ = call("GET", "/api/recommend?topK=5")
check("未登录也能拿到推荐", rec is not None and len(rec["dishes"]) == 5)
check("权重和 AHP 算出来的一致",
      all(abs(w["value"] - e) < 5e-4 for w, e in zip(
          rec["weights"], [0.3784, 0.2529, 0.1917, 0.1166, 0.0605])),
      [round(w["value"], 4) for w in rec["weights"]])
check("返回了正负理想解（推荐实验室要展示）",
      len(rec["ideal"]) == 5 and len(rec["anti"]) == 5)
check("每条推荐都有理由", all(d["reasons"] for d in rec["dishes"]))
check("贴近度落在 0-1", all(0 <= d["closeness"] <= 1 for d in rec["dishes"]))
check("贴近度降序", all(rec["dishes"][i]["closeness"] >= rec["dishes"][i + 1]["closeness"]
                        for i in range(len(rec["dishes"]) - 1)))
check("名次从 1 连续", [d["rank"] for d in rec["dishes"]] == [1, 2, 3, 4, 5])

# =====================================================================
section("注册与冷启动")
import random
newphone = "139" + "".join(str(random.randint(0, 9)) for _ in range(8))
try:
    _, reg, _ = call("POST", "/api/auth/register",
                     {"username": newphone, "password": "test123", "nickname": "冒烟测试",
                      "phone": newphone})
    check("新用户注册成功", reg["user"]["role"] == "CUSTOMER")
    check("新用户等级是「新客」", reg["user"]["level"] == "新客", reg["user"]["level"])
    newtoken = reg["token"]

    _, newrec, _ = call("GET", "/api/recommend?topK=3", token=newtoken)
    check("刚注册、零历史的用户也能拿到推荐（冷启动）",
          newrec is not None and len(newrec["dishes"]) == 3)
    check("新客的推荐理由不带「属于你偏好的口味分类」",
          all("属于你偏好的口味分类" not in d["reasons"] for d in newrec["dishes"]))
except AssertionError as e:
    check("注册流程", False, str(e))
    newtoken = None

# =====================================================================
section("口味偏好影响推荐")
token = login("13800000621", "123456")
_, base, _ = call("GET", "/api/recommend?topK=20", token=token)
top_before = {d["code"] for d in base["dishes"][:5]}

_, taste, _ = call("GET", "/api/profile/taste", token=token)
check("能读到已存在的口味设置", isinstance(taste["preferCategories"], list))
check("返回了可选的忌口清单", len(taste["ingredientOptions"]) > 0)

_, saved, _ = call("PUT", "/api/profile/taste",
                   {"avoidIngredients": ["牛羊肉"]}, token=token)
check("忌口保存成功", "牛羊肉" in saved["avoidIngredients"], saved["avoidIngredients"])

_, after, _ = call("GET", "/api/recommend?topK=20", token=token)
beefy = [d for d in after["dishes"]
         if any(k in d["name"] for k in ("羊", "牛"))]
check("忌口之后，含牛羊的菜排名下降了或已被压到后面",
      all(d["rank"] > 3 for d in beefy) if beefy else True,
      [(d["name"], d["rank"]) for d in beefy][:3])

# 恢复，免得影响后面的演示数据
call("PUT", "/api/profile/taste", {"avoidIngredients": []}, token=token)

# =====================================================================
section("下单与计费")
before_stock = [d for d in call("GET", f"/api/dishes/{pick_a['code']}")[1:] if False] or None
_, d0, _ = call("GET", f"/api/dishes/{pick_a['code']}")
stock_before = d0["stock"]

_, order, _ = call("POST", "/api/orders", {
    "tableCode": "A12", "dineMode": "DINE_IN", "payMethod": "微信支付", "remark": "冒烟测试",
    "items": [{"dishCode": pick_a["code"], "qty": 2}],
}, token=token)

check("堂食订单创建成功", order["status"] == "PENDING", order["status"])
expected_sub = round(pick_a["price"] * 2, 2)
check("小计算对了", abs(order["subtotal"] - expected_sub) < 0.011,
      f'{order["subtotal"]} vs {expected_sub}')
check("堂食不收服务费", order["serviceFee"] == 0, order["serviceFee"])
check("堂食没有自提立减", order["discount"] == 0, order["discount"])
check("堂食实付 = 菜品小计", abs(order["total"] - order["subtotal"]) < 0.011,
      f'{order["total"]} vs {order["subtotal"]}')
check("订单行带菜名快照", order["items"][0]["dishName"] == pick_a["name"])

_, d1, _ = call("GET", f"/api/dishes/{pick_a['code']}")
check("库存真的扣了 2", d1["stock"] == stock_before - 2,
      f'{stock_before} -> {d1["stock"]}')

# 自取
_, takeaway, _ = call("POST", "/api/orders", {
    "dineMode": "TAKEOUT", "payMethod": "支付宝",
    "items": [{"dishCode": pick_b["code"], "qty": 1}],
}, token=token)
check("自取不收服务费", takeaway["serviceFee"] == 0, takeaway["serviceFee"])
expected_discount = round(min(4, float(takeaway["subtotal"]) * 0.02), 2)
check("自取有立减（小计的 2%，最多 4 元）",
      abs(float(takeaway["discount"]) - expected_discount) < 0.011,
      f'{takeaway["discount"]} vs {expected_discount}')
check("自取实付 = 小计 − 立减",
      abs(float(takeaway["total"]) - (float(takeaway["subtotal"]) - float(takeaway["discount"]))) < 0.011,
      takeaway["total"])
check("自取比堂食便宜", float(takeaway["total"]) < float(takeaway["subtotal"]),
      f'{takeaway["total"]} vs {takeaway["subtotal"]}')
check("自取可以没有桌号", takeaway["tableCode"] is None, takeaway["tableCode"])

# =====================================================================
section("取消订单要退回库存")
_, d2, _ = call("GET", f"/api/dishes/{pick_a['code']}")
s_before_cancel = d2["stock"]
_, cancelled, _ = call("POST", f"/api/orders/{order['orderNo']}/cancel", token=token)
check("订单被取消", cancelled["status"] == "CANCELLED", cancelled["status"])
_, d3, _ = call("GET", f"/api/dishes/{pick_a['code']}")
check("库存退回来了", d3["stock"] == s_before_cancel + 2,
      f'{s_before_cancel} -> {d3["stock"]}')
check("已取消的单不能再取消",
      call("POST", f"/api/orders/{order['orderNo']}/cancel", token=token,
           expect=4002)[2]["code"] == 4002)

# =====================================================================
section("后厨流转")
k_token = login("13900000002", "kitchen123")
_, queue, _ = call("GET", "/api/kitchen/orders", token=k_token)
check("后厨能看到进行中的单", any(o["orderNo"] == takeaway["orderNo"] for o in queue),
      len(queue))

_, cooking, _ = call("POST", f"/api/kitchen/orders/{takeaway['orderNo']}/status?to=COOKING",
                     token=k_token)
check("待接单 → 制作中", cooking["status"] == "COOKING", cooking["status"])

_, served, _ = call("POST", f"/api/kitchen/orders/{takeaway['orderNo']}/status?to=SERVED",
                    token=k_token)
check("制作中 → 已上菜", served["status"] == "SERVED", served["status"])

illegal = call("POST", f"/api/kitchen/orders/{takeaway['orderNo']}/status?to=COOKING",
               token=k_token, expect=4002)
check("倒退流转被状态机挡住", illegal[2]["code"] == 4002, illegal[2]["message"])

_, done, _ = call("POST", f"/api/kitchen/orders/{takeaway['orderNo']}/status?to=DONE",
                  token=k_token)
check("已上菜 → 已完成", done["status"] == "DONE", done["status"])
check("到终态后不能再流转",
      call("POST", f"/api/kitchen/orders/{takeaway['orderNo']}/status?to=CANCELLED",
           token=k_token, expect=4002)[2]["code"] == 4002)

# =====================================================================
section("后台看板")
a_token = login("13900000001", "admin123")
_, dash, _ = call("GET", "/api/admin/dashboard", token=a_token)
check("看板返回今日营业额", "todayRevenue" in dash, list(dash)[:5])
check("14 天趋势补齐了每一天", len(dash["revenueTrend"]) == 14, len(dash["revenueTrend"]))
check("趋势里没有空洞（缺订单的日子补 0）",
      all("amount" in p and "label" in p for p in dash["revenueTrend"]))
check("分类占比按金额降序",
      all(dash["categoryShare"][i]["amount"] >= dash["categoryShare"][i + 1]["amount"]
          for i in range(len(dash["categoryShare"]) - 1)))
check("客单价 = 营业额 / 单数",
      dash["todayOrders"] == 0
      or abs(dash["avgTicket"] - dash["todayRevenue"] / dash["todayOrders"]) < 0.011,
      f'{dash["avgTicket"]} vs {dash["todayRevenue"]}/{dash["todayOrders"]}')
check("状态分布含全部状态", set(dash["statusCount"]) ==
      {"PENDING", "COOKING", "SERVED", "DONE", "CANCELLED"}, list(dash["statusCount"]))

# =====================================================================
section("越权访问必须被挡住")
kw = call("POST", "/api/kitchen/orders/X/status?to=COOKING", token=token, expect=None)
check("顾客不能调后厨接口", kw[0] == 403, f"HTTP {kw[0]}")
ad = call("GET", "/api/admin/dashboard", token=token, expect=None)
check("顾客不能看后台看板", ad[0] == 403, f"HTTP {ad[0]}")
kb = call("GET", "/api/admin/dashboard", token=k_token, expect=None)
check("后厨不能看经营看板", kb[0] == 403, f"HTTP {kb[0]}")
noauth = call("GET", "/api/auth/me", expect=None)
check("未登录访问需要登录的接口返回 401", noauth[0] == 401, f"HTTP {noauth[0]}")
bad = call("GET", "/api/orders", token="not-a-real-token", expect=None)
check("伪造的令牌被拒", bad[0] == 401, f"HTTP {bad[0]}")

other = call("GET", f"/api/orders/{takeaway['orderNo']}", token=newtoken, expect=None) \
    if newtoken else (0, None, {"code": 0})
check("看不到别人的订单", other[2]["code"] == 2002, other[2])

# =====================================================================
print(f"\n{'=' * 46}")
print(f"通过 {passed} 项，失败 {failed} 项")
print("=" * 46)
sys.exit(1 if failed else 0)
