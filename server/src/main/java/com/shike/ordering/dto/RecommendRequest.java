package com.shike.ordering.dto;

import java.util.List;

/**
 * 推荐请求。「推荐实验室」页面的全部可调项都在这里。
 *
 * <p>所有字段都可选。什么都不传就是「按 AHP 默认权重、用当前登录人的画像、
 * 在全部在售菜品里推荐」—— 也就是首页那个默认入口。
 *
 * @param weights          五项准则的权重，顺序与 {@code Criterion} 声明顺序一致
 *                         （口味、价格、热度、等待、清淡）。拖滑杆时传。
 *                         服务端会归一化，所以传未归一的原始值也行
 * @param preferCategories 临时覆盖偏好分类。「试试只看烧烤和蒸点会怎样」用得到，
 *                         覆盖结果不落库
 * @param maxSpice         临时覆盖辣度上限
 * @param avoidSpice       临时覆盖「完全不吃辣」
 * @param avoidIngredients 临时覆盖忌口。演示时现场加一项，
 *                         立刻能看到那几道菜掉下去 —— 这是最好讲的一个效果
 * @param category         只在某个分类里推荐。传 {@code signature} 表示只推招牌菜
 * @param topK             只返回前 k 名。不传返回全部（推荐实验室要摊开全部距离）
 */
public record RecommendRequest(
        List<Double> weights,
        List<String> preferCategories,
        Integer maxSpice,
        Boolean avoidSpice,
        List<String> avoidIngredients,
        String category,
        Integer topK
) {
}
