package net.maku.followcom.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 系统侦测到的订单变化类型
 * 1-NEW: 开仓 挂单
 * 2-MODIFIED  修改挂单的 修改开仓的订单
 * 3-DELETED 删除挂单
 * 4-CLOSED 关闭开仓的订单
 * 5-ACTIVE 持仓订单
 * 使用枚举类型比直接使用整形的好处是，在传参时候使用枚举变量，代码更具有易读性。
 * mybatis-plus官方文档https://mp.baomidou.com/guide/enum.html
 * <p>
 * 注意：对于系统中的类型字段类型尽量选着varchar，这样便于解决业务上需要in查询的时候，前端通过字符串中包含多个类型值（类型之间通过逗号隔开）传递到后端。
 */
@Getter
public enum OrderChangeTypeEnum implements IEnum<String> {
    /**
     *
     */
    NEW("NEW", "新开开单"), MODIFIED("MODIFIED", "修改订单"), DELETED("DELETED", "删除订单"), CLOSED("CLOSED", "关闭订单");

    /**
     * 枚举对象的值
     * EnumValue注解决定实体对象写入数据中使用的值
     * JsonValue后端将实体对象以json格式数据返回时使用的值
     */
    @EnumValue
    @JsonValue
    private final String value;

    private final String description;

    /**
     * @param val  枚举对象的常量值
     * @param desc 枚举对象的描述
     */
    OrderChangeTypeEnum(String val, String desc) {
        this.value = val;
        this.description = desc;
    }

    @Override
    public String getValue() {
        return this.value;
    }
}