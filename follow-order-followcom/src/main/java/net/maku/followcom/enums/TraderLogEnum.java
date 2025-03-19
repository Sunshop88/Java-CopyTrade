package net.maku.followcom.enums;


import lombok.Getter;

@Getter
public enum TraderLogEnum{
    MASTER(0, "策略管理"),

    SLAVE(1, "跟单管理"),

    FOLLOW_OPERATION(2, "跟单操作");

    private Integer type;

    private String description;

    /**
     * @param desc 枚举对象的描述
     */
    TraderLogEnum(Integer type,  String desc) {
        this.type= type;
        this.description = desc;
    }

}
