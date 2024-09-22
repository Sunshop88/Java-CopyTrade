package net.maku.followcom.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 跟单方向
 * 1-正向
 * 2-反向
 */
@Getter
public enum DirectionEnum implements IEnum<String> {
    /*
     * */
    FORWARD(0,"FORWARD", "正向"),
    REVERSE(1,"REVERSE", "反向");

    private Integer type;
    /**
     * 枚举对象的值
     * EnumValue注解决定实体对象写入数据中使用的值
     * JsonValue后端将实体对象以json格式数据返回时使用的值
     */
    @EnumValue
    @JsonValue
    private String value;

    private String description;

    /**
     * @param val  枚举对象的常量值
     * @param desc 枚举对象的描述
     */
    DirectionEnum(Integer type,String val, String desc) {
        this.type = type;
        this.value = val;
        this.description = desc;
    }
}
