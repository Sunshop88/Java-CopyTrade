package net.maku.followcom.enums;


import lombok.Getter;


@Getter
public enum MfaVerifyEnum {

    /**
     * 用户MFA是否已认证（1：已认证；0：未认证）
     */
    NOT_CERTIFIED(0),
    CERTIFIED(1),

    /**
     * 是否开启MFA认证（1：是；0：否）
     */
    NOT_START_CERTIFIED(0),
    START_CERTIFIED(1);
    private Integer type;

    MfaVerifyEnum(Integer type) {
        this.type = type;
    }

}
