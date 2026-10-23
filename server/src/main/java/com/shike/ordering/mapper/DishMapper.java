package com.shike.ordering.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shike.ordering.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Dish 的持久层。
 *
 * <p>继承 BaseMapper 就拿到了增删改查；只有多表联查和统计
 * 才需要在这里手写 SQL —— MyBatis-Plus 的 LambdaQuery 表达不了聚合。
 */
@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 扣库存，<b>扣不动就返回 0</b>。
     *
     * <p>关键在于 {@code AND stock >= #{qty}} 这个条件写在 SQL 里，
     * 而不是先查再判断再更新。后者在两人同时点最后一份时会双双通过检查，
     * 库存变成 -1。数据库对同一行的 UPDATE 是串行的，
     * 把判断放进 WHERE 就把"检查"和"扣减"合成了一个原子操作。
     *
     * <p>调用方必须检查返回值：返回 0 表示库存不够，要回滚并告诉用户。
     *
     * @return 影响行数。1 表示扣成功，0 表示库存不足或菜品不存在
     */
    @Update("UPDATE t_dish SET stock = stock - #{qty} " +
            "WHERE id = #{id} AND stock >= #{qty} AND deleted = 0")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 取消订单时把库存还回去 */
    @Update("UPDATE t_dish SET stock = stock + #{qty} WHERE id = #{id} AND deleted = 0")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}
