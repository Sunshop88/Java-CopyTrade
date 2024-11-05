package net.maku.followcom.enums;


import lombok.Getter;

@Getter
public enum TraderLogTypeEnum {
    ADD(0, "新增"),

    UPDATE(1, "修改"),

    DELETE(2, "删除"),

    SEND(3, "下单"),

    CLOSE(4, "平仓"),

    REPAIR(5, "补单");


    private Integer type;

    private String description;

    /**
     * @param desc 枚举对象的描述
     */
    TraderLogTypeEnum(Integer type, String desc) {
        this.type= type;
        this.description = desc;
    }

}
