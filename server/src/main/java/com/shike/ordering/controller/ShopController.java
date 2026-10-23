package com.shike.ordering.controller;

import com.shike.ordering.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 门店信息。
 *
 * <p>页头、页脚、小票抬头都要显示店名、地址、营业时间。这几项如果让前端
 * 各写一份，就会出现"页脚改了地址、小票还是旧的"这种不一致。
 * 从配置里统一发一份出去，各处都读它。
 *
 * <p>做成接口而不是前端常量，还因为这几项将来大概率要挪到数据库 ——
 * 到那时只要换掉这个 Controller 的实现，前端一行都不用动。
 */
@RestController
public class ShopController {

    private final Map<String, String> shop;

    public ShopController(@Value("${shike.shop.name}") String name,
                          @Value("${shike.shop.sub}") String sub,
                          @Value("${shike.shop.slogan}") String slogan,
                          @Value("${shike.shop.phone}") String phone,
                          @Value("${shike.shop.address}") String address,
                          @Value("${shike.shop.hours}") String hours) {
        // 用 LinkedHashMap 固定顺序，让接口返回的 JSON 读起来稳定，
        // 便于对照文档、也便于 diff
        this.shop = new LinkedHashMap<>();
        shop.put("name", name);
        shop.put("sub", sub);
        shop.put("slogan", slogan);
        shop.put("phone", phone);
        shop.put("address", address);
        shop.put("hours", hours);
    }

    @GetMapping("/api/shop")
    public ApiResponse<Map<String, String>> shop() {
        return ApiResponse.ok(shop);
    }
}
