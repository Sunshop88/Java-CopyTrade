package net.maku.followcom.enums;

import lombok.Getter;

@Getter
public enum VpsSpendEnum {
    FAILURE(0),
    IN_PROGRESS(1),
    SUCCESS(2);
    private Integer type;

    VpsSpendEnum(Integer type) {
        this.type = type;
    }
}
