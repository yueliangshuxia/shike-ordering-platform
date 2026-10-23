-- =====================================================================
--  食刻点餐系统 · 建表脚本
--  适用于 MySQL 8.0+，同时兼容 H2 的 MySQL 模式（单元测试用）。
--
--  设计说明：
--   1. 表名统一加 t_ 前缀，避免和 MySQL 保留字撞车（order、user 都是保留字）。
--   2. 金额一律 DECIMAL，不用 FLOAT/DOUBLE —— 浮点存钱迟早出现
--      ¥264.59999999999997 这种输出。
--   3. 几乎所有表都有 created_at / updated_at，是论文 4.2 节「数据审计」的要求。
--   4. 逻辑删除用 deleted 字段，不真删。订单是要留档的。
--   5. 建表语句写成 IF NOT EXISTS，插入写成幂等语句，所以应用每次启动
--      重复执行这个脚本也不会破坏已有数据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. t_user 用户
--    顾客、后厨、管理员是同一张表，靠 role 区分。
--    分开建三张表在这里没有收益：三者共享登录、昵称、状态这些字段，
--    而鉴权本来就要按角色分支，合成一张表反而少一次 join。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL                COMMENT '登录账号，用手机号',
    password    VARCHAR(100) NOT NULL                COMMENT 'BCrypt 摘要。绝不放明文',
    nickname    VARCHAR(32)  NOT NULL                COMMENT '昵称，页面右上角显示这个',
    phone       VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
    role        VARCHAR(16)  NOT NULL DEFAULT 'CUSTOMER' COMMENT 'CUSTOMER 顾客 / KITCHEN 后厨 / ADMIN 管理员',
    level       VARCHAR(16)  NOT NULL DEFAULT '新客'  COMMENT '新客 / 常客 / 熟客，由历史订单数算出来',
    avatar      VARCHAR(255)          DEFAULT NULL   COMMENT '头像地址',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除 0 未删 / 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表（顾客 / 后厨 / 管理员）';

-- ---------------------------------------------------------------------
-- 2. t_user_taste 用户口味画像
--    推荐算法里「口味匹配」这项准则的唯一数据来源。
--    和 t_user 拆开是因为：它被读写的时机完全不同 ——
--    登录时不会碰它，而每次出推荐都要读它。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user_taste (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL COMMENT '所属用户',
    prefer_categories  VARCHAR(255)          DEFAULT NULL COMMENT '偏好分类，逗号分隔的分类 code，如 barbecue,dimsum',
    max_spice          TINYINT      NOT NULL DEFAULT 3    COMMENT '可接受辣度上限 0-3',
    avoid_spice        TINYINT      NOT NULL DEFAULT 0    COMMENT '是否完全不吃辣',
    avoid_ingredients  VARCHAR(255)          DEFAULT NULL COMMENT '忌口食材，逗号分隔，如 牛羊肉,香菜',
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_taste_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户口味画像';

-- ---------------------------------------------------------------------
-- 3. t_category 菜品分类
--    注意：这里<b>只有 8 个真实分类</b>，「招牌推荐」不在这张表里。
--    它是一个虚拟分类 —— 没有任何一道菜的 cat 是 signature，
--    「招牌」是打在菜品上的标签。接口层会把它拼在返回列表的最前面。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_category (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    code     VARCHAR(32) NOT NULL COMMENT '分类代号，如 dimsum。算法和前端都用这个',
    name     VARCHAR(32) NOT NULL COMMENT '显示名，如 蒸点',
    icon     VARCHAR(64)          DEFAULT NULL COMMENT '图标名，如 mdi:pot-steam',
    sort     INT         NOT NULL DEFAULT 0 COMMENT '展示顺序，越小越靠前',
    enabled  TINYINT     NOT NULL DEFAULT 1 COMMENT '是否启用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品分类（不含虚拟分类「招牌推荐」）';

-- ---------------------------------------------------------------------
-- 4. t_dish 菜品
--    price 用 DECIMAL(8,2)：能表示到分，也不会像 FLOAT 那样出现 42.00000001。
--    sold / rating 是「近 30 天」的滚动统计，由订单表定时汇总，不是每次现算 ——
--    几十道菜还好，菜一多就不能让推荐接口去扫全量订单了。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_dish (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32)   NOT NULL COMMENT '菜品编码，如 D1001',
    name        VARCHAR(64)   NOT NULL COMMENT '菜名',
    category_id BIGINT        NOT NULL COMMENT '所属分类，外键指向 t_category.id',
    price       DECIMAL(8, 2) NOT NULL COMMENT '单价（元）',
    img         VARCHAR(255)           DEFAULT NULL COMMENT '实拍图。留空时前端生成带菜名的占位图块',
    brief       VARCHAR(255)           DEFAULT NULL COMMENT '一句话卖点，列表页显示',
    detail      VARCHAR(1000)          DEFAULT NULL COMMENT '详情文案，详情页显示',
    spice       TINYINT       NOT NULL DEFAULT 0 COMMENT '辣度 0 不辣 / 1 微辣 / 2 中辣 / 3 特辣',
    sold        INT           NOT NULL DEFAULT 0 COMMENT '近 30 天销量，热度准则的输入',
    rating      DECIMAL(3, 1) NOT NULL DEFAULT 5.0 COMMENT '评分，满分 5',
    prep_min    INT           NOT NULL DEFAULT 10 COMMENT '出餐时长（分钟），等待准则的输入',
    stock       INT           NOT NULL DEFAULT 0 COMMENT '剩余库存，0 表示售罄',
    unit        VARCHAR(8)             DEFAULT NULL COMMENT '计价单位，如 笼 / 份 / 煲',
    spec_label  VARCHAR(16)            DEFAULT NULL COMMENT '规格选择器上方的标签，如 份量 / 辣度',
    enabled     TINYINT       NOT NULL DEFAULT 1 COMMENT '是否上架',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dish_code (code),
    KEY idx_dish_category (category_id),
    KEY idx_dish_enabled (enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品';

-- ---------------------------------------------------------------------
-- 5. t_dish_tag 菜品标签
--    标签决定推荐逻辑里的几处分支：招牌（虚拟分类命中）、素食（清淡加分）、
--    冰饮（清淡减分）、现做。拆成独立表而不是在 t_dish 里塞逗号串，
--    是为了能按标签建索引直接筛，也为了将来统计「本月卖得最好的招牌菜」。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_dish_tag (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    dish_id BIGINT      NOT NULL,
    tag     VARCHAR(16) NOT NULL COMMENT '招牌 / 素食 / 冰饮 / 现做',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dish_tag (dish_id, tag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品标签';

-- ---------------------------------------------------------------------
-- 6. t_dish_ingredient 菜品食材
--    忌口过滤的数据来源。算法对每项忌口做「子串 + 别名」匹配，
--    所以这里存的是食材名原文（牛肉末、河蟹黄），不是编码。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_dish_ingredient (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    dish_id    BIGINT      NOT NULL,
    name       VARCHAR(32) NOT NULL COMMENT '食材名原文，如 牛肉末',
    sort       INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 唯一键不只是约束：它让灌演示数据的 INSERT IGNORE 变成幂等的，
    -- 应用每次启动重跑 data.sql 不会把食材行插成两份
    UNIQUE KEY uk_ingredient (dish_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品食材（忌口过滤用）';

-- ---------------------------------------------------------------------
-- 7. t_dish_spec 菜品规格
--    同一道菜的不同规格是<b>两条订单行</b>，行的身份是 dish_id#spec_id。
--    price_delta 是相对基准价的加价，不是绝对价 —— 改基础价时不用逐条改规格。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_dish_spec (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    dish_id     BIGINT        NOT NULL,
    name        VARCHAR(32)   NOT NULL COMMENT '规格名，如 大份 10 只',
    price_delta DECIMAL(8, 2) NOT NULL DEFAULT 0 COMMENT '相对基准价的加价（元）',
    sort        INT           NOT NULL DEFAULT 0 COMMENT '展示顺序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_spec (dish_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜品规格';

-- ---------------------------------------------------------------------
-- 8. t_dining_table 餐桌
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_dining_table (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    code     VARCHAR(16) NOT NULL COMMENT '桌号，如 A12',
    seats    INT         NOT NULL DEFAULT 4 COMMENT '座位数',
    area     VARCHAR(32)          DEFAULT NULL COMMENT '区域，如 一层大厅',
    status   VARCHAR(16) NOT NULL DEFAULT 'FREE' COMMENT 'FREE 空闲 / OCCUPIED 就餐中 / RESERVED 已预订',
    enabled  TINYINT     NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_table_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='餐桌';

-- ---------------------------------------------------------------------
-- 9. t_order 订单
--    status 流转：PENDING 待接单 → COOKING 制作中 → SERVED 已上菜 → DONE 已完成，
--    任意非终态都可以 → CANCELLED 已取消。
--
--    金额拆成四列存下来而不是每次用明细现算：
--    单价会变，订单一旦成交，它的金额就必须被冻结。这也是电子小票的合规要求。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_order (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    order_no     VARCHAR(32)   NOT NULL COMMENT '订单号，如 SK20261015001',
    user_id      BIGINT        NOT NULL COMMENT '下单用户',
    table_id     BIGINT                 DEFAULT NULL COMMENT '餐桌。外带时为空',
    status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COOKING/SERVED/DONE/CANCELLED',
    pay_method   VARCHAR(16)   NOT NULL DEFAULT '未支付' COMMENT '微信支付 / 支付宝 / 未支付',
    remark       VARCHAR(255)           DEFAULT NULL COMMENT '备注，如 少放花椒',
    subtotal     DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '菜品小计',
    service_fee  DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '服务费。当前不收，比例配成 0，机制保留',
    discount     DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '自提立减（正数表示减掉多少）。堂食为 0',
    total        DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '实付合计 = 小计 + 服务费 − 立减',
    dine_mode    VARCHAR(16)   NOT NULL DEFAULT 'DINE_IN' COMMENT 'DINE_IN 堂食 / TAKEOUT 自取',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    finished_at  DATETIME               DEFAULT NULL COMMENT '完成时间，算翻台率用',
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_user (user_id),
    KEY idx_order_status (status),
    KEY idx_order_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单';

-- ---------------------------------------------------------------------
-- 10. t_order_item 订单明细
--     dish_name / spec_name / unit_price 都存<b>快照</b>，不外键回查。
--     菜名和价格以后会改，但历史订单必须原样重放得出当时那张小票。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_order_item (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    order_id   BIGINT        NOT NULL,
    dish_id    BIGINT        NOT NULL,
    spec_id    BIGINT                 DEFAULT NULL COMMENT '规格 id。没有规格时为空',
    dish_name  VARCHAR(64)   NOT NULL COMMENT '下单时的菜名快照',
    spec_name  VARCHAR(32)            DEFAULT NULL COMMENT '下单时的规格名快照',
    unit_price DECIMAL(8, 2) NOT NULL COMMENT '下单时的单价快照（含规格加价）',
    qty        INT           NOT NULL COMMENT '数量',
    amount     DECIMAL(10, 2) NOT NULL COMMENT '小计 = 单价 × 数量',
    PRIMARY KEY (id),
    KEY idx_item_order (order_id),
    KEY idx_item_dish (dish_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细（菜名与价格均为下单时快照）';
