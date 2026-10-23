package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.dto.CategoryVO;
import com.shike.ordering.dto.DishVO;
import com.shike.ordering.entity.DiningTable;
import com.shike.ordering.mapper.DiningTableMapper;
import com.shike.ordering.service.DishService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单的公开接口。全部匿名可访问 —— 不登录也能看菜、看价格。
 *
 * <p>这不是偷懒：堂食的实际情况就是顾客先坐下看菜单再决定要不要注册。
 * 强制登录只会把人挡在门外，而菜单本身没有任何敏感信息。
 */
@RestController
@RequestMapping("/api")
public class MenuController {

    private final DishService dishService;
    private final DiningTableMapper tableMapper;

    public MenuController(DishService dishService, DiningTableMapper tableMapper) {
        this.dishService = dishService;
        this.tableMapper = tableMapper;
    }

    /** 分类列表。第一个永远是虚拟分类「招牌推荐」 */
    @GetMapping("/categories")
    public ApiResponse<List<CategoryVO>> categories() {
        return ApiResponse.ok(dishService.listCategories());
    }

    /**
     * 菜品列表。
     *
     * @param category        分类 code。传 {@code signature} 取招牌菜
     * @param tag             按标签筛，如 素食
     * @param includeSoldOut  是否带上售罄的菜（默认带，菜单页要展示并置灰）
     */
    @GetMapping("/dishes")
    public ApiResponse<List<DishVO>> dishes(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "true") boolean includeSoldOut) {
        return ApiResponse.ok(dishService.listDishes(category, tag, includeSoldOut));
    }

    @GetMapping("/dishes/{code}")
    public ApiResponse<DishVO> dish(@PathVariable String code) {
        return ApiResponse.ok(dishService.getByCode(code));
    }

    /** 餐桌列表。扫码点餐时前端要校验桌号存不存在 */
    @GetMapping("/tables")
    public ApiResponse<List<DiningTable>> tables() {
        return ApiResponse.ok(tableMapper.selectList(Wrappers.<DiningTable>lambdaQuery()
                .eq(DiningTable::getEnabled, 1)
                .orderByAsc(DiningTable::getCode)));
    }
}
