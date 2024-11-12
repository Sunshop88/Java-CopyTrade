package net.maku.followcom.enums;


import lombok.Getter;


@Getter
public enum TraderRepairEnum {

    SEND(0, "下单"),
    CLOSE(1,"平仓");

    private Integer type;


    //枚举对象的描述
    private String description;

    /**
     * @param val  枚举对象的常量值
     * @param desc 枚举对象的描述
     */
    TraderRepairEnum(Integer type, String desc) {
        this.type = type;
        this.description = desc;
    }

}
