/* 食刻点餐 · 静态演示数据
 *
 * 这份数据是前端 Demo 的假数据，后端演示数据由它生成
 * （node tools/gen_seed.mjs → server/.../db/data.sql），不要手改生成的 SQL。
 *
 * ── 这家长什么样 ───────────────────────────────────────────────
 * **港式茶餐厅**。之前那份菜单横跨蒸点、烧烤、火锅、拉面、甜品 ——
 * 一家小馆不可能什么都做，那是它最「编」的地方。茶餐厅是真实存在的业态，
 * 而且它的菜单本来就是杂的：早茶点心、粥粉面、碟头小炒、糖水、丝袜奶茶
 * 全都卖，所以这份菜单看着杂，其实符合真实经营。
 *
 * 收掉的是**烧烤和火锅** —— 这两样茶餐厅不做，也是原来最违和的部分。
 *
 * ── 图片来源 ─────────────────────────────────────────────────
 *   pexels-*.jpg      Pexels 真实菜品照片（已下载到本地，见 demo/README.md）
 *   <英文菜名>.jpg     用户自己补的图，来源未标注，不属于 Pexels 那一批
 *   留空的            没有实拍图，前端按菜名生成虚线占位图块
 *
 * 2026-10-22 补齐了最后 4 道缺图的菜（艇仔粥、杨枝甘露、冻柠茶、菊花茶），
 * 现在 20 道菜都有实拍图。占位图机制保留着 —— 以后新增菜忘了配图时，
 * 页面会显示虚线块而不是空白或随机照片，一眼能看出问题。
 */

/* ---------- 门店信息 ---------- */
const SHOP = {
  name: '食刻',
  sub: 'SHI KE KITCHEN',
  slogan: '现做现出，等你开饭',
  phone: '0592-8888 6666',
  address: '厦门市集美区理工路 600 号 一层 08 铺',
  hours: '每日 07:30 — 22:00（21:30 停止点单）',
  tables: [
    { id: 'A12', seats: 4, area: '一层大厅' },
    { id: 'A13', seats: 4, area: '一层大厅' },
    { id: 'B07', seats: 6, area: '二层雅座' },
    { id: 'B08', seats: 2, area: '二层雅座' },
    { id: 'C01', seats: 8, area: '包厢' }
  ]
};

/* ---------- 分类 ----------
 * 「招牌推荐」是虚拟分类 —— 没有任何一道菜的 cat 是 signature，
 * 「招牌」是打在菜上的 tag。取分类的菜一律走 SK.dishesOf()，
 * 自己写 list.filter(d => d.cat === id) 会让「招牌推荐」永远是空的。 */
const CATEGORIES = [
  { id: 'signature', name: '招牌推荐', icon: 'mdi:chef-hat' },
  { id: 'dimsum',    name: '点心蒸点', icon: 'mdi:pot-steam' },
  { id: 'congee',    name: '粥粉面',   icon: 'mdi:noodles' },
  { id: 'stirfry',   name: '小炒碟头', icon: 'mdi:pot-mix-outline' },
  { id: 'dessert',   name: '糖水甜品', icon: 'mdi:ice-cream' },
  { id: 'drink',     name: '饮品',     icon: 'mdi:cup' }
];

/* ---------- 菜品 ----------
 * spice    0 不辣 / 1 微辣 / 2 中辣 / 3 特辣
 * tags     推荐算法的判别字段：招牌 / 素食 / 冰饮 / 现做
 * sold     近 30 天销量，TOPSIS 的热度指标
 * rating   评分，TOPSIS 的口碑指标
 * prepMin  出餐时长（分钟），TOPSIS 的等待成本指标
 * stock    剩余库存，0 表示售罄
 */
const DISHES = [
  /* ===== 点心蒸点 ===== */
  {
    id: 'D1001', name: '水晶虾饺', cat: 'dimsum', price: 28,
    img: 'assets/img/dishes/pexels-38141840.jpg',
    desc: '整只青虾仁，澄面皮透亮弹牙',
    detail: '每只塞进三尾整虾仁，澄面加木薯粉擀成半透明皮，蒸出来能看见里面的虾。褶数要够十二道，收口捏紧才不会漏汤。蘸一点浙醋提鲜。',
    tags: ['招牌', '现做'], spice: 0, sold: 1286, rating: 4.8, prepMin: 10, stock: 48,
    unit: '笼', specLabel: '份量',
    ingredients: ['青虾仁', '澄面', '木薯粉', '笋粒'],
    specs: [
      { name: '标准 4 只', delta: 0 },
      { name: '大份 8 只', delta: 26 }
    ]
  },
  {
    id: 'D1002', name: '蟹黄小笼包', cat: 'dimsum', price: 38,
    img: 'assets/img/dishes/pexels-36453292.jpg',
    desc: '现包现蒸，皮薄十八褶，一口爆汤',
    detail: '每日清晨现拆蟹黄拌入猪前腿肉，高汤冻打底。上笼猛火蒸六分钟即出，褶花收在顶部，提起来不破皮。吃法是先开窗、后吸汤、再蘸姜醋。',
    tags: ['招牌', '现做'], spice: 0, sold: 1145, rating: 4.8, prepMin: 12,
    /* stock 为 0 = 售罄。菜谱里留一道售罄的是有意的：
       菜单页要能演示"置灰 + 售罄角标"，后台要能显示"售罄 1 道"，
       下单时也要能走到"这道菜已经卖完了"那条分支。
       蟹黄按当日拆的量走，拆完为止，售罄是常态。 */
    stock: 0,
    unit: '笼', specLabel: '份量',
    ingredients: ['河蟹黄', '猪前腿肉', '高汤冻', '高筋面粉'],
    specs: [
      { name: '标准 6 只', delta: 0 },
      { name: '大份 10 只', delta: 22 }
    ]
  },
  {
    id: 'D1003', name: '韭菜蒸饺', cat: 'dimsum', price: 24,
    img: 'assets/img/dishes/pexels-27927337.jpg',
    desc: '烫面皮，韭菜配鸡蛋碎，素得香',
    detail: '韭菜只用中段，鸡蛋炒成细碎晾凉再拌，避免出水。烫面皮蒸后偏软糯，比水饺更适合配茶。',
    tags: ['素食'], spice: 0, sold: 862, rating: 4.5, prepMin: 9, stock: 52,
    unit: '笼', specLabel: '份量',
    ingredients: ['韭菜', '土鸡蛋', '中筋面粉', '香油'],
    specs: [
      { name: '标准 4 只', delta: 0 },
      { name: '大份 8 只', delta: 18 }
    ]
  },
  {
    id: 'D1004', name: '明档鲜肉包', cat: 'dimsum', price: 12,
    img: 'assets/img/dishes/pexels-17317227.jpg',
    desc: '门口明档现蒸，掀开笼盖就是香',
    detail: '门口蒸炉全天不停火，什么时候路过都是热的。肉馅加葱姜水和皮冻，咬开有汁。早市七点半出第一笼。',
    tags: ['现做'], spice: 0, sold: 1620, rating: 4.6, prepMin: 8, stock: 120,
    unit: '个', specLabel: '数量',
    ingredients: ['猪前腿肉', '中筋面粉', '葱姜水', '皮冻'],
    specs: [
      { name: '单个', delta: 0 },
      { name: '三个', delta: 22 }
    ]
  },
  {
    id: 'D1005', name: '粤式早茶拼盘', cat: 'dimsum', price: 58,
    img: 'assets/img/dishes/pexels-33623761.jpg',
    desc: '虾饺烧卖肠粉三拼，一笼吃全',
    detail: '早茶时段最受欢迎的搭配：鲜虾饺两件、干蒸烧卖两件、鲜虾肠粉一条，另配一壶普洱。适合两个人分着吃。',
    tags: ['招牌'], spice: 0, sold: 508, rating: 4.9, prepMin: 14, stock: 30,
    unit: '份', specLabel: '搭配',
    ingredients: ['青虾仁', '猪肉', '肠粉', '普洱茶叶'],
    specs: [
      { name: '双人份', delta: 0 },
      { name: '四人份', delta: 46 }
    ]
  },

  /* ===== 粥粉面 ===== */
  {
    id: 'D2001', name: '鲜虾云吞面', cat: 'congee', price: 32,
    img: 'assets/img/dishes/pexels-31263150.jpg',
    desc: '大地鱼熬汤，云吞里包整只虾',
    detail: '汤底用大地鱼干、猪骨和虾头熬四小时，喝起来是清甜不是咸鲜。云吞现包，一只里放整尾虾仁；面用碱水细面，过冷水再回汤，才够爽。',
    tags: ['招牌', '现做'], spice: 0, sold: 1086, rating: 4.7, prepMin: 9, stock: 45,
    unit: '碗', specLabel: '份量',
    ingredients: ['青虾仁', '猪前腿肉', '碱水面', '大地鱼干'],
    specs: [
      { name: '标准', delta: 0 },
      { name: '加面', delta: 6 }
    ]
  },
  {
    id: 'D2002', name: '星洲炒米粉', cat: 'congee', price: 34,
    img: 'assets/img/dishes/pexels-9213853.jpg',
    desc: '咖喱香配银芽，米粉根根分明',
    detail: '细米粉先用温水泡软再炒，火要大、手要快，不然会坨。咖喱粉和虾米吊味，出锅前拌银芽和韭黄，保持脆口。',
    tags: ['现做'], spice: 2, sold: 724, rating: 4.6, prepMin: 10, stock: 38,
    unit: '份', specLabel: '份量',
    ingredients: ['细米粉', '银芽', '韭黄', '虾米', '咖喱粉'],
    specs: [
      { name: '标准', delta: 0 },
      { name: '大份', delta: 12 }
    ]
  },
  {
    id: 'D2003', name: '姜葱捞面', cat: 'congee', price: 22,
    img: 'assets/img/dishes/pexels-31418923.jpg',
    desc: '葱油现泼，拌开就是香',
    detail: '姜蓉和葱花铺在面上，滚油一泼激出香味再拌。看着素，吃过的回头点得最多。加一只溏心蛋更下饭。',
    tags: ['素食'], spice: 0, sold: 962, rating: 4.4, prepMin: 7, stock: 60,
    unit: '碗', specLabel: '份量',
    ingredients: ['细面', '姜', '葱', '花生油'],
    specs: [
      { name: '标准', delta: 0 },
      { name: '加溏心蛋', delta: 5 }
    ]
  },
  {
    id: 'D2004', name: '叉烧汤面', cat: 'congee', price: 38,
    img: 'assets/img/dishes/pexels-31264126.jpg',
    desc: '自家烧的叉烧切片，汤头清甜',
    detail: '叉烧是店里自己烧的，和烧味拼盘用的是同一炉。切片铺在汤面上，另配半只溏心蛋。汤底用猪骨和大地鱼熬，不加酱油，澄清见底。',
    tags: [], spice: 0, sold: 834, rating: 4.7, prepMin: 10, stock: 32,
    unit: '碗', specLabel: '份量',
    ingredients: ['叉烧', '细面', '土鸡蛋', '猪骨', '大地鱼干'],
    specs: [
      { name: '标准', delta: 0 },
      { name: '加叉烧', delta: 16 }
    ]
  },
  {
    id: 'D2005', name: '艇仔粥', cat: 'congee', price: 20,
    img: 'assets/img/dishes/tinzai-congee.jpg',
    desc: '鱼片猪皮花生，一碗十样料',
    detail: '白粥底熬到开花，上桌前才把鲩鱼片、猪皮丝、炸花生、油条碎一样样码进去，靠粥的余温烫熟鱼片。料比粥多，是老广的吃法。',
    tags: ['现做'], spice: 0, sold: 1120, rating: 4.5, prepMin: 6, stock: 40,
    unit: '碗', specLabel: '份量',
    ingredients: ['鲩鱼片', '猪皮', '炸花生', '油条', '大米'],
    specs: [
      { name: '标准', delta: 0 },
      { name: '大份', delta: 8 }
    ]
  },

  /* ===== 小炒碟头 ===== */
  {
    id: 'D3001', name: '避风塘炒虾', cat: 'stirfry', price: 68,
    img: 'assets/img/dishes/pexels-10201775.jpg',
    desc: '蒜酥铺满，连壳都能嚼着吃',
    detail: '大虾开背去线，先炸到壳脆，再和金银蒜、面包糠、干辣椒一起翻。蒜要分两次下，一半炸香一半留生，香气才有层次。',
    tags: ['招牌', '现做'], spice: 1, sold: 724, rating: 4.8, prepMin: 14, stock: 26,
    unit: '份', specLabel: '份量',
    ingredients: ['海虾', '蒜蓉', '面包糠', '干辣椒'],
    specs: [
      { name: '标准', delta: 0 },
      { name: '大份', delta: 24 }
    ]
  },
  {
    id: 'D3002', name: '豉油皇煎虾', cat: 'stirfry', price: 58,
    img: 'assets/img/dishes/pexels-36720618.jpg',
    desc: '头抽加冰糖，壳亮得反光',
    detail: '只用头抽和冰糖调汁，不加老抽，所以颜色透亮不发黑。虾下锅前剪掉长须，两面煎透再淋汁收干，汁挂在壳上才算到位。',
    tags: ['现做'], spice: 0, sold: 618, rating: 4.7, prepMin: 12, stock: 24,
    unit: '份', specLabel: '份量',
    ingredients: ['海虾', '头抽', '冰糖', '姜'],
    specs: [
      { name: '标准', delta: 0 },
      { name: '大份', delta: 22 }
    ]
  },
  {
    id: 'D3003', name: '麻婆豆腐', cat: 'stirfry', price: 32,
    img: 'assets/img/dishes/pexels-37297770.jpg',
    desc: '麻、辣、烫、香，配饭一流',
    detail: '嫩豆腐先用盐水浸过，下锅才不容易碎。郫县豆瓣炒出红油，起锅前撒花椒粉。茶餐厅的碟头饭常点它，一勺豆腐能送下半碗饭。',
    tags: ['现做'], spice: 2, sold: 1673, rating: 4.6, prepMin: 8, stock: 50,
    unit: '份', specLabel: '辣度',
    ingredients: ['嫩豆腐', '牛肉末', '郫县豆瓣', '花椒'],
    specs: [
      { name: '微辣', delta: 0 },
      { name: '中辣', delta: 0 }
    ]
  },
  {
    id: 'D3004', name: '白灼时蔬', cat: 'stirfry', price: 26,
    img: 'assets/img/dishes/pexels-1640777.jpg',
    desc: '当日市蔬，灼好淋豉油',
    detail: '蔬菜按当日市价调整，通常有菜心、芥蓝或西兰花。滚水加一点油盐灼熟，捞起淋热豉油——这一步是把青菜做得比肉还香的关键。',
    tags: ['素食'], spice: 0, sold: 486, rating: 4.4, prepMin: 6, stock: 46,
    unit: '份', specLabel: '份量',
    ingredients: ['菜心', '西兰花', '蒜', '生抽'],
    specs: [
      { name: '标准', delta: 0 },
      { name: '大份', delta: 10 }
    ]
  },

  /* ===== 糖水甜品 ===== */
  {
    id: 'D4001', name: '焦糖炖蛋', cat: 'dessert', price: 26,
    img: 'assets/img/dishes/pexels-34711174.jpg',
    desc: '现烤焦糖壳，敲开才好吃',
    detail: '蛋奶液过筛两次保证顺滑，上桌前现撒糖用喷枪烤出脆壳。壳会慢慢回软，建议五分钟内敲开。',
    tags: ['现做'], spice: 0, sold: 476, rating: 4.7, prepMin: 9, stock: 28,
    unit: '份', specLabel: '份量',
    ingredients: ['土鸡蛋', '淡奶油', '香草荚', '焦糖'],
    specs: [
      { name: '单份', delta: 0 },
      { name: '双份', delta: 20 }
    ]
  },
  {
    id: 'D4002', name: '草莓芝士蛋糕', cat: 'dessert', price: 32,
    img: 'assets/img/dishes/pexels-30794003.jpg',
    desc: '免烤芝士，草莓按季节换',
    detail: '饼底用消化饼干压实，芝士层低温冷藏六小时定型。草莓只在当季用新鲜的，过季换成蓝莓或芒果。',
    tags: ['冰饮'], spice: 0, sold: 638, rating: 4.8, prepMin: 4, stock: 24,
    unit: '块', specLabel: '份量',
    ingredients: ['奶油芝士', '消化饼干', '淡奶油', '当季草莓'],
    specs: [
      { name: '单块', delta: 0 },
      { name: '双人份 2 块', delta: 26 }
    ]
  },
  {
    id: 'D4003', name: '杨枝甘露', cat: 'dessert', price: 28,
    img: 'assets/img/dishes/mango-pomelo-sago.jpg',
    desc: '芒果西柚椰浆，港式老配方',
    detail: '芒果打底、西柚拆成粒、椰浆和淡奶油调匀，最后撒西米。西柚要拆干净白膜，不然会苦。',
    tags: ['冰饮'], spice: 0, sold: 592, rating: 4.6, prepMin: 6, stock: 26,
    unit: '份', specLabel: '份量',
    ingredients: ['芒果', '西柚', '椰浆', '西米'],
    specs: [
      { name: '单份', delta: 0 },
      { name: '大份', delta: 12 }
    ]
  },

  /* ===== 饮品 ===== */
  {
    id: 'D5001', name: '港式丝袜奶茶', cat: 'drink', price: 18,
    img: 'assets/img/dishes/pexels-34511977.jpg',
    desc: '茶胆撞奶，浓到挂杯',
    detail: '锡兰红茶拼配三种粗细的茶，撞茶要撞六手才够滑。淡奶最后下，不能煮，煮了会出油。热的比冻的香，但冻的配碟头饭更顺口。',
    tags: ['冰饮', '现做'], spice: 0, sold: 1486, rating: 4.7, prepMin: 4, stock: 80,
    unit: '杯', specLabel: '冷热',
    ingredients: ['锡兰红茶', '淡奶', '砂糖'],
    specs: [
      { name: '热', delta: 0 },
      { name: '冻', delta: 2 }
    ]
  },
  {
    id: 'D5002', name: '冻柠茶', cat: 'drink', price: 16,
    img: 'assets/img/dishes/iced-lemon-tea.jpg',
    desc: '柠檬现切现捶，茶底清亮',
    detail: '柠檬要现切现捶，捶出皮油才够香，提前捶好放一会儿会发苦。茶底比奶茶淡一档，糖浆单独上，客人自己调甜度。',
    tags: ['冰饮'], spice: 0, sold: 1620, rating: 4.6, prepMin: 3, stock: 90,
    unit: '杯', specLabel: '甜度',
    ingredients: ['柠檬', '锡兰红茶', '糖浆'],
    specs: [
      { name: '标准甜', delta: 0 },
      { name: '少糖', delta: 0 }
    ]
  },
  {
    id: 'D5003', name: '菊花茶', cat: 'drink', price: 12,
    img: 'assets/img/dishes/chrysanthemum-tea.jpg',
    desc: '杭白菊加冰糖，解腻',
    detail: '整朵杭白菊泡开，加一小块冰糖。吃多了炸物和烧味的时候，一壶下去最舒服。可以续水两次。',
    tags: ['冰饮'], spice: 0, sold: 684, rating: 4.3, prepMin: 2, stock: 100,
    unit: '壶', specLabel: '冷热',
    ingredients: ['杭白菊', '冰糖'],
    specs: [
      { name: '热', delta: 0 },
      { name: '冻', delta: 0 }
    ]
  }
];

/* ---------- 用户口味画像（推荐算法输入） ----------
 * 这份权重就是 AHP 层次分析法算出来的判断矩阵结果，
 * 演示页里可以手动调，实时看推荐排序怎么变。
 */
const USER_PROFILE = {
  user: { id: 'U220171207', name: '陈同学', phone: '138****6621', avatar: '', level: '常客' },
  table: 'A12',
  weights: {
    taste: 0.34,   // 口味匹配
    price: 0.24,   // 价格实惠
    hot:   0.20,   // 热度口碑
    wait:  0.16,   // 出餐速度
    healthy: 0.06  // 清淡健康
  },
  preferCats: ['signature', 'dimsum', 'congee'],
  avoidSpice: false,
  maxSpice: 3
};

/* 推荐准则，供 TOPSIS 展示用 */
const CRITERIA = [
  { key: 'taste',   name: '口味匹配', unit: '',   benefit: true,  desc: '和你历史点单、偏好分类的契合度' },
  { key: 'price',   name: '价格实惠', unit: '元', benefit: false, desc: '单价越低得分越高' },
  { key: 'hot',     name: '热度口碑', unit: '',   benefit: true,  desc: '近 30 天销量与评分的综合' },
  { key: 'wait',    name: '出餐速度', unit: '分', benefit: false, desc: '等待时间越短得分越高' },
  { key: 'healthy', name: '清淡健康', unit: '',   benefit: true,  desc: '辣度越低、素食越多得分越高' }
];

/* ---------- 历史订单（个人中心 / 后台看板用） ----------
 * 菜品点得集中在点心和粥粉面，和上面的 preferCats 对得上 ——
 * 隐式行为（历史）和显式偏好（勾选）能互相印证，演示时说得通。 */
const ORDERS = [
  {
    no: 'SK20261015001', time: '2026-10-15 12:18', table: 'A12', status: '已下单',
    items: [
      { id: 'D1001', qty: 2 }, { id: 'D2001', qty: 1 }, { id: 'D5001', qty: 2 }
    ],
    pay: '微信支付', remark: '虾饺不要姜醋'
  },
  {
    no: 'SK20261014007', time: '2026-10-14 19:42', table: 'B07', status: '已完成',
    items: [
      { id: 'D1002', qty: 1 }, { id: 'D2004', qty: 1 }, { id: 'D3001', qty: 1 }, { id: 'D4001', qty: 1 }
    ],
    pay: '支付宝', remark: ''
  },
  {
    no: 'SK20261012003', time: '2026-10-12 12:05', table: 'A13', status: '已完成',
    items: [
      { id: 'D1004', qty: 3 }, { id: 'D2005', qty: 1 }, { id: 'D5002', qty: 2 }
    ],
    pay: '微信支付', remark: '打包带走'
  },
  {
    no: 'SK20261008011', time: '2026-10-08 18:30', table: 'C01', status: '已取消',
    items: [
      { id: 'D3003', qty: 1 }, { id: 'D2002', qty: 2 }
    ],
    pay: '未支付', remark: '临时有事'
  },
  {
    no: 'SK20261006002', time: '2026-10-06 08:15', table: 'A12', status: '已完成',
    items: [
      { id: 'D1004', qty: 2 }, { id: 'D1003', qty: 1 }, { id: 'D5003', qty: 1 }
    ],
    pay: '微信支付', remark: '早市'
  }
];

/* 近 14 天营业额，后台图表用 */
const REVENUE_14D = [
  { d: '10-02', v: 8420 }, { d: '10-03', v: 11260 }, { d: '10-04', v: 12840 },
  { d: '10-05', v: 10310 }, { d: '10-06', v: 9860 }, { d: '10-07', v: 13520 },
  { d: '10-08', v: 14280 }, { d: '10-09', v: 9160 }, { d: '10-10', v: 8940 },
  { d: '10-11', v: 12080 }, { d: '10-12', v: 13470 }, { d: '10-13', v: 10920 },
  { d: '10-14', v: 12760 }, { d: '10-15', v: 8610 }
];

/* 分类销售占比，后台饼图用。分类要和上面的 CATEGORIES 对得上 */
const CAT_SHARE = [
  { name: '点心蒸点', v: 27 }, { name: '粥粉面', v: 24 }, { name: '小炒碟头', v: 21 },
  { name: '饮品', v: 17 }, { name: '糖水甜品', v: 11 }
];

/* 后厨出单队列，后厨屏用 */
const KITCHEN_QUEUE = [
  { no: 'SK20261015001', table: 'A12', waitMin: 2,  status: '待接单',
    items: [{ name: '水晶虾饺', qty: 2 }, { name: '鲜虾云吞面', qty: 1 }, { name: '港式丝袜奶茶', qty: 2 }] },
  { no: 'SK20261015002', table: 'B08', waitMin: 6,  status: '制作中',
    items: [{ name: '清汤牛腩面', qty: 1 }, { name: '蟹黄小笼包', qty: 1 }] },
  { no: 'SK20261015003', table: 'B07', waitMin: 11, status: '制作中',
    items: [{ name: '避风塘炒虾', qty: 1 }, { name: '麻婆豆腐', qty: 1 }, { name: '冻柠茶', qty: 3 }] },
  { no: 'SK20261015004', table: 'C01', waitMin: 17, status: '待上菜',
    items: [{ name: '粤式早茶拼盘', qty: 2 }, { name: '豉油皇煎虾', qty: 1 }, { name: '草莓芝士蛋糕', qty: 4 }] }
];

/* 暴露到全局，供各页面的脚本取用 */
window.SHOP = SHOP;
window.CATEGORIES = CATEGORIES;
window.DISHES = DISHES;
window.USER_PROFILE = USER_PROFILE;
window.CRITERIA = CRITERIA;
window.ORDERS = ORDERS;
window.REVENUE_14D = REVENUE_14D;
window.CAT_SHARE = CAT_SHARE;
window.KITCHEN_QUEUE = KITCHEN_QUEUE;
