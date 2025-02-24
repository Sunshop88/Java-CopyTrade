package net.maku.followcom.enums;

import lombok.Getter;

@Getter
public enum TraderUserEnum {
    //处理中
    IN_PROGRESS(0),
    //处理成功
    SUCCESS(1);
    private Integer type;

    TraderUserEnum(int type) {
        this.type = type;
    }
}
