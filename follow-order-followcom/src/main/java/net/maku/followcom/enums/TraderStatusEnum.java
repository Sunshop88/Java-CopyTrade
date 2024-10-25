package net.maku.followcom.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum TraderStatusEnum {
    NORMAL(0, "正常"),
    ERROR(1, "异常");


    @EnumValue
    @JsonValue
    private Integer value;

    private String description;

    /**
     * @param val  枚举对象的常量值
     * @param desc 枚举对象的描述
     */
    TraderStatusEnum(Integer val, String desc) {
        this.value = val;
        this.description = desc;
    }

}
