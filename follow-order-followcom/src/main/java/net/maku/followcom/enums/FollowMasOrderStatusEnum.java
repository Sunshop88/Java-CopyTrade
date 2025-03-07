package net.maku.followcom.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum FollowMasOrderStatusEnum implements IEnum<Integer> {

    UNDERWAY(0, "执行中"),
    ALLSUCCESS(1, "全部成功"),
    PARTIALFAILURE(2, "部分失败");

    @EnumValue
    @JsonValue
    private Integer value;

    /**
     * 该枚举值的描述
     */
    private String description;

    /**
     * @param val  枚举对象的常量值
     * @param desc 枚举对象的描述
     */
    FollowMasOrderStatusEnum(Integer val, String desc) {
        this.value = val;
        this.description = desc;
    }

    @Override
    public Integer getValue() {
        return this.value;
    }
}
