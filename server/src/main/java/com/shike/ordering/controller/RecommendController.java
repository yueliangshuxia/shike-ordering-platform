package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import com.shike.ordering.dto.RecommendRequest;
import com.shike.ordering.dto.RecommendResponse;
import com.shike.ordering.security.LoginUser;
import com.shike.ordering.service.RecommendationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个性化推荐。<b>登录和未登录都能调</b>。
 *
 * <p>这是本方案相对协同过滤最核心的优势所在：协同过滤在用户没有历史行为时
 * 给不出结果（冷启动问题），而这里的新客第一次打开就能拿到一份
 * 带理由的推荐 —— 因为五个准则里有四个只依赖菜品自身属性，
 * 唯一需要画像的「口味匹配」也有基准分兜底。
 *
 * <p>所以这个接口刻意不做登录校验。要是在这里加一道登录墙，
 * 这个优势就完全演示不出来了。
 */
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendationService recommendationService;

    public RecommendController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * 默认推荐。首页和菜单页的「猜你想吃」都用这个。
     *
     * <p>GET 而不是 POST，因为它没有副作用、参数也少，
     * 浏览器和 CDN 都能直接缓存，前端的调用代码也更短。
     */
    @GetMapping
    public ApiResponse<RecommendResponse> recommend(
            @AuthenticationPrincipal LoginUser me,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) String category) {

        RecommendRequest req = new RecommendRequest(
                null, null, null, null, null, category, topK);
        return ApiResponse.ok(recommendationService.recommend(idOf(me), req));
    }

    /**
     * 带参数的推荐。「推荐实验室」拖滑杆、勾忌口走的就是这个。
     *
     * <p>用 POST 是因为参数是一个结构化的对象（五个权重 + 四项偏好），
     * 塞进查询串既难看又有长度风险。
     */
    @PostMapping
    public ApiResponse<RecommendResponse> recommendWith(
            @AuthenticationPrincipal LoginUser me,
            @RequestBody(required = false) RecommendRequest req) {
        return ApiResponse.ok(recommendationService.recommend(idOf(me), req));
    }

    /** 未登录时返回 null —— 服务层会退化成「新客画像」，而不是报错 */
    private static Long idOf(LoginUser me) {
        return me == null ? null : me.id();
    }
}
