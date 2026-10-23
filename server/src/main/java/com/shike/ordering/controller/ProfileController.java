package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.dto.TasteRequest;
import com.shike.ordering.entity.UserTasteProfile;
import com.shike.ordering.security.LoginUser;
import com.shike.ordering.service.TasteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人中心：口味偏好。
 *
 * <p>口味偏好是推荐算法的输入之一，所以这个页面不是"设置项"，
 * 而是算法的一部分 —— 用户在这里改一下，下次推荐就跟着变。
 * 界面上应当把这句话说明白，否则用户不会知道填它有什用。
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final TasteService tasteService;

    public ProfileController(TasteService tasteService) {
        this.tasteService = tasteService;
    }

    /** 当前口味设置。没设置过时也返回一份默认值，前端不必处理 null */
    @GetMapping("/taste")
    public ApiResponse<Map<String, Object>> taste(@AuthenticationPrincipal LoginUser me) {
        UserTasteProfile p = tasteService.profileOf(me.id());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("preferCategories", p == null ? List.of() : p.preferCategoryList());
        out.put("maxSpice", p == null || p.getMaxSpice() == null ? 3 : p.getMaxSpice());
        out.put("avoidSpice", p != null && Integer.valueOf(1).equals(p.getAvoidSpice()));
        out.put("avoidIngredients", p == null ? List.of() : p.avoidIngredientList());
        // 可选项一并返回：忌口用自由文本输入的话，用户写「牛肉」而库里的食材是
        // 「肥牛卷」，就匹配不上了。给一份建议清单能让这件事可靠很多
        out.put("categoryOptions", tasteService.selectableCategories());
        out.put("ingredientOptions", COMMON_AVOID_INGREDIENTS);
        return ApiResponse.ok(out);
    }

    @PutMapping("/taste")
    public ApiResponse<Map<String, Object>> saveTaste(@AuthenticationPrincipal LoginUser me,
                                                      @RequestBody TasteRequest req) {
        tasteService.save(me.id(), req);
        return taste(me);
    }

    /**
     * 常见的忌口食材建议。
     *
     * <p>写死在这里是权衡后的选择：它是"建议值"不是"权限清单"，
     * 用户可以自己填别的；而为了它建一张表、配一套后台维护界面，
     * 收益不抵成本。真做起来应该由商家按自家菜单维护。
     */
    private static final List<String> COMMON_AVOID_INGREDIENTS = List.of(
            "内脏", "牛羊肉", "香菜", "葱", "蒜", "花生", "海鲜", "辣椒", "菌菇");
}
