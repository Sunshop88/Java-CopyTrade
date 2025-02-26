package net.maku.followcom.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;


@Getter
public enum TraderTypeEnum implements IEnum<String> {

    SLAVE_REAL(1,"SLAVE_REAL", "跟单者"),
    MASTER_REAL(0,"MASTER_REAL", "喊单者"),
    ALL(2,"ALL", "交易分配");

    private Integer type;

    @EnumValue
    @JsonValue
    private String value;

    //枚举对象的描述
    private String description;

    /**
     * @param val  枚举对象的常量值
     * @param desc 枚举对象的描述
     */
    TraderTypeEnum(Integer type,String val, String desc) {
        this.type = type;
        this.value = val;
        this.description = desc;
    }

    public static TraderTypeEnum getTraderTypeEnum(Integer type) {
        TraderTypeEnum[] enums = TraderTypeEnum.values();
        for (TraderTypeEnum anEnum : enums) {
            if (type.equals(anEnum.getType())) {
                return anEnum;
            }
        }
        return null;
    }

    public static TraderTypeEnum getTraderTypeEnum(String val) {
        TraderTypeEnum[] enums = TraderTypeEnum.values();
        for (TraderTypeEnum anEnum : enums) {
            if (val.equals(anEnum.getValue())) {
                return anEnum;
            }
        }
        return null;
    }

}
