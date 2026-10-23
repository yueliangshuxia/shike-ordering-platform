package com.shike.ordering.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户口味画像。推荐算法里「口味匹配」准则的数据来源。
 *
 * <p>名字故意叫 Profile 而不是 Taste —— 算法包里已经有一个
 * {@link com.shike.ordering.algorithm.UserTaste}，两个类同名会让
 * 「哪个是数据库行、哪个是算法输入」变得难分辨。
 * 两者的转换只在一个地方做：{@code TasteService.toAlgorithmInput()}。
 *
 * <p>逗号分隔的字段（偏好分类、忌口）在实体里提供 {@link #preferCategoryList()}
 * 之类的读取方法，避免每个调用方各自 split 一遍、还各自忘了去掉空串。
 */
@Data
@TableName("t_user_taste")
public class UserTasteProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 逗号分隔的分类 code，如 "barbecue,dimsum" */
    private String preferCategories;

    /** 可接受辣度上限 0–3 */
    private Integer maxSpice;

    private Integer avoidSpice;

    /** 逗号分隔的忌口食材，如 "牛羊肉,香菜" */
    private String avoidIngredients;

    private LocalDateTime updatedAt;

    public List<String> preferCategoryList() {
        return splitCsv(preferCategories);
    }

    public List<String> avoidIngredientList() {
        return splitCsv(avoidIngredients);
    }

    /** 空串和 null 都当「没填」，不要返回一个含空字符串的列表 */
    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
