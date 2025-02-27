package com.flink.enums;


import lombok.Getter;

@Getter
public enum TraderRepairOrderEnum {
    SEND(0, "未跟单"),

    CLOSE(1, "未平仓");


    private Integer type;

    private String description;

    /**
     * @param desc 枚举对象的描述
     */
    TraderRepairOrderEnum(Integer type, String desc) {
        this.type= type;
        this.description = desc;
    }

    public static TraderRepairOrderEnum getTraderRepairOrderEnum(Integer type) {
        TraderRepairOrderEnum[] enums = TraderRepairOrderEnum.values();
        for (TraderRepairOrderEnum anEnum : enums) {
            if (type.equals(anEnum.getType())) {
                return anEnum;
            }
        }
        return null;
    }
}
