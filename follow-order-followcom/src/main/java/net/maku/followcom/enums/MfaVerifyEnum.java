package net.maku.followcom.enums;


import lombok.Getter;


@Getter
public enum MfaVerifyEnum {

    NOT_CERTIFIED(0),
    CERTIFIED(1);
    private Integer type;

    MfaVerifyEnum(Integer type) {
        this.type = type;
    }

}
