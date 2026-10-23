/* 食刻点餐 · 个性化推荐模块
 *
 * 两段式：先用 AHP 层次分析法确定各评价准则的权重，
 * 再用 TOPSIS 逼近理想解排序法给菜品打分排序。
 *
 * 为什么不用协同过滤：堂食场景的新客占比较高，用户-菜品评分矩阵极稀疏，
 * 协同过滤的冷启动问题很重。AHP-TOPSIS 只依赖菜品自身属性和用户显式偏好，
 * 新客第一次打开就能给出可解释的推荐结果。
 *
 * 本文件是前端演示实现，逻辑与后端 Java 版一一对应，
 * 论文第 3 章给的就是这套公式。
 */

const SKR = (() => {
  'use strict';

  /* ---------- 一、AHP：确定准则权重 ---------- */

  /* 随机一致性指标 RI，Saaty 给的对照表，n=1..10 */
  const RI = { 1: 0, 2: 0, 3: 0.58, 4: 0.90, 5: 1.12, 6: 1.24, 7: 1.32, 8: 1.41, 9: 1.45, 10: 1.49 };

  /* 判断矩阵：a[i][j] 表示准则 i 相对准则 j 的重要程度，1-9 标度
     下面是本系统默认的一份矩阵，可以由店铺在后台调整。
     行列顺序：口味匹配 / 价格实惠 / 热度口碑 / 出餐速度 / 清淡健康 */
  const DEFAULT_JUDGMENT = [
    [1,    2,    2,    3,    5  ],
    [1/2,  1,    2,    2,    4  ],
    [1/2,  1/2,  1,    2,    4  ],
    [1/3,  1/2,  1/2,  1,    2  ],
    [1/5,  1/4,  1/4,  1/2,  1  ]
  ];

  /**
   * 层次分析法求权重。
   * 用方根法（几何平均）算特征向量，再做归一化。
   * 最后做一致性检验，CR < 0.1 才算通过。
   */
  function ahp(matrix) {
    const A = matrix || DEFAULT_JUDGMENT;
    const n = A.length;

    // 1. 每行求几何平均
    const geo = A.map((row) => Math.pow(row.reduce((p, v) => p * v, 1), 1 / n));
    const geoSum = geo.reduce((a, b) => a + b, 0);

    // 2. 归一化得到权重向量 W
    const weights = geo.map((g) => g / geoSum);

    // 3. 求最大特征根 λmax = Σ (AW)_i / (n · W_i)
    const AW = A.map((row) => row.reduce((s, v, j) => s + v * weights[j], 0));
    let lambdaMax = 0;
    for (let i = 0; i < n; i += 1) lambdaMax += AW[i] / (n * weights[i]);

    // 4. 一致性检验
    const CI = (lambdaMax - n) / (n - 1);
    const ri = RI[n] || 1.12;
    const CR = ri === 0 ? 0 : CI / ri;

    return { weights, lambdaMax, CI, CR, RI: ri, pass: CR < 0.1 };
  }

  /* ---------- 二、构造决策矩阵 ---------- */

  /* 忌口：用户勾掉的食材出现在配料里就压分。
     只做子串匹配，不做同义词表 —— 认真的食材知识库是后端该干的事，
     演示阶段用一张小别名表够表达意图，而且用户看得懂为什么被压。
     默认画像不带 avoid，所以不动忌口时推荐结果与之前完全一致。 */
  const AVOID_ALIAS = {
    内脏: ['肝', '肚', '肠', '腰', '胗', '毛肚'],
    牛羊肉: ['牛', '羊']
  };

  /* 长得像、但明明不是那类食材的名字，命中就放过。
   * 子串匹配的代价：不列这张名单的话，勾「忌口内脏」会把含红腰豆的
   * 田园时蔬沙拉和含牛肝菌的菌油拌面一起扣分 —— 一个是豆子一个是菌菇。
   * 必须和 server 端 CriterionScorer.AVOID_FALSE_FRIENDS 保持一致。 */
  const AVOID_FALSE_FRIENDS = ['红腰豆', '腰果', '牛肝菌'];

  /** 单个食材是否命中某一项忌口。打分和推荐理由共用这一个判断 */
  function matchesAvoid(ing, a) {
    if (AVOID_FALSE_FRIENDS.includes(ing)) return false;
    const needles = AVOID_ALIAS[a] || [a];
    return needles.some((n) => ing.includes(n));
  }

  function avoidHits(dish, profile) {
    const avoid = profile?.avoid || [];
    if (!avoid.length) return 0;
    return avoid.filter((a) => (dish.ingredients || []).some((ing) => matchesAvoid(ing, a))).length;
  }

  /**
   * 口味匹配度：菜品属性与用户偏好的契合程度，归一到 0-100。
   * 这是唯一一个需要用户画像才能算出来的准则，其余四项都是菜品自身属性。
   */
  function tasteScore(dish, profile) {
    let s = 40;

    // 分类命中用户偏好
    if (profile.preferCats.includes(dish.cat)) s += 34;
    if (profile.preferCats.includes('signature') && dish.tags.includes('招牌')) s += 10;

    // 辣度贴合：差值越小加分越多
    const wantSpice = profile.avoidSpice ? 0 : 1;
    s += (3 - Math.abs(dish.spice - wantSpice)) * 5;

    // 超过用户辣度上限直接压下去
    if (dish.spice > profile.maxSpice) s -= 45;

    // 命中忌口食材，每命中一项再压一档
    s -= avoidHits(dish, profile) * 30;

    return Math.max(0, Math.min(100, s));
  }

  /* 热度口碑：销量和评分各占一半。销量开方是为了压掉头部菜品的量级优势，
     否则几千串的烧烤会把所有蒸点挤出去。 */
  function hotScore(dish) {
    const soldPart = Math.sqrt(dish.sold) / Math.sqrt(4300) * 100;
    const ratingPart = (dish.rating / 5) * 100;
    return Math.min(100, soldPart * 0.5 + ratingPart * 0.5);
  }

  /* 清淡健康：不辣加分，素食加分 */
  function healthyScore(dish) {
    let s = 100 - dish.spice * 26;
    if (dish.tags.includes('素食')) s += 12;
    if (dish.tags.includes('冰饮')) s -= 6;
    return Math.max(0, Math.min(100, s));
  }

  /**
   * 生成决策矩阵。每行一道菜，每列一个准则。
   * 返回的 raw 是原始值（价格是元、等待是分钟），
   * 归一化在 topsis 里做，保留原始值是为了在界面上展示。
   */
  function buildMatrix(dishes, profile) {
    return dishes.map((d) => {
      const raw = {
        taste: tasteScore(d, profile),
        price: d.price,
        hot: hotScore(d),
        wait: d.prepMin,
        healthy: healthyScore(d)
      };
      return { dish: d, raw };
    });
  }

  /* ---------- 三、TOPSIS：逼近理想解排序 ---------- */

  /**
   * TOPSIS 主过程。
   * criteria 里 benefit=true 表示效益型指标（越大越好），false 为成本型（越小越好）。
   *
   * 步骤：
   *   1. 向量归一化 r_ij = x_ij / sqrt(Σ_i x_ij²)
   *   2. 加权 v_ij = w_j · r_ij
   *   3. 定正理想解 V+ 和负理想解 V-
   *   4. 算各方案到两者的欧氏距离 D+、D-
   *   5. 贴近度 C_i = D- / (D+ + D-)，按 C_i 降序排
   */
  function topsis(matrix, weights, criteria) {
    const m = matrix.length;
    const n = criteria.length;
    if (!m) return [];

    const keys = criteria.map((c) => c.key);

    // 1. 向量归一化
    const sqSum = keys.map((k) => Math.sqrt(matrix.reduce((s, row) => s + row.raw[k] ** 2, 0)) || 1);
    const norm = matrix.map((row) => keys.map((k, j) => row.raw[k] / sqSum[j]));

    // 2. 加权
    const weighted = norm.map((row) => row.map((v, j) => v * weights[keys[j]]));

    // 3. 正负理想解
    const ideal = keys.map((k, j) => {
      const col = weighted.map((row) => row[j]);
      return criteria[j].benefit ? Math.max(...col) : Math.min(...col);
    });
    const anti = keys.map((k, j) => {
      const col = weighted.map((row) => row[j]);
      return criteria[j].benefit ? Math.min(...col) : Math.max(...col);
    });

    // 4. 距离
    const dist = weighted.map((row) => {
      let dPlus = 0, dMinus = 0;
      row.forEach((v, j) => {
        dPlus += (v - ideal[j]) ** 2;
        dMinus += (v - anti[j]) ** 2;
      });
      return { dPlus: Math.sqrt(dPlus), dMinus: Math.sqrt(dMinus) };
    });

    // 5. 贴近度
    const scored = matrix.map((row, i) => {
      const { dPlus, dMinus } = dist[i];
      const denom = dPlus + dMinus;
      return {
        dish: row.dish,
        raw: row.raw,
        dPlus,
        dMinus,
        closeness: denom === 0 ? 0 : dMinus / denom
      };
    });

    scored.sort((a, b) => b.closeness - a.closeness);
    scored.forEach((s, i) => { s.rank = i + 1; });

    /* 把正负理想解挂在返回的数组上。推荐实验室要把 V⁺/V⁻ 摊开给用户看，
       必须给他们真正参与过距离计算的这一组（归一化+加权之后的值）。
       另起炉灶拿原始值再算一遍列极值，算出来的数跟 D⁺/D⁻ 对不上。 */
    scored.keys = keys;
    scored.ideal = ideal;
    scored.anti = anti;
    return scored;
  }

  /* ---------- 四、对外入口 ---------- */

  /**
   * 完整推荐流程。
   * @param dishes   候选菜品（已过滤售罄）
   * @param profile  用户画像
   * @param weights  准则权重；不传就用 AHP 默认矩阵算出来的
   */
  function recommend(dishes, profile, weights) {
    const criteria = window.CRITERIA;
    const w = weights || ahp().weights.reduce((acc, v, i) => {
      acc[criteria[i].key] = v; return acc;
    }, {});

    const available = dishes.filter((d) => d.stock > 0);
    const matrix = buildMatrix(available, profile);
    return topsis(matrix, w, criteria);
  }

  /* 把 AHP 结果转成 {准则key: 权重} 的形式，方便调权重滑杆时复用 */
  function ahpWeights(matrix) {
    const keys = window.CRITERIA.map((c) => c.key);
    return ahp(matrix).weights.reduce((acc, v, i) => { acc[keys[i]] = v; return acc; }, {});
  }

  return { ahp, ahpWeights, buildMatrix, topsis, recommend, tasteScore, hotScore, healthyScore, avoidHits, DEFAULT_JUDGMENT, RI };
})();

window.SKR = SKR;
