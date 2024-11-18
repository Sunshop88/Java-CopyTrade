package net.maku.followcom.enums;

/**
 * Author:  zsd
 * Date:  2024/11/15/周五 18:19
 */

public enum FollowModeEnum {
    //跟随模式0-固定手数 1-手数比例 2-净值比例
    FIX(0, 2, "固定手数"),
    RATIO(1, 1, "手数比例"),
    WORTH_RATIO(2, 0, "净值比例"),
    BALANCE_RATIO(3, 3, "资金比例(余额)"),
    CUSTOM_RATIO(4, 4, "自定义");

    private final Integer code;

    private final Integer apiCode;
    private final String description;

    FollowModeEnum(Integer code, Integer apiCode, String description) {
        this.code = code;
        this.apiCode = apiCode;
        this.description = description;
    }


    public static Integer getVal(Integer apiCode) {
        Integer val = RATIO.code;
        switch (apiCode) {
            case 0:
                val = WORTH_RATIO.code;
                break;
            case 1:
                val = RATIO.code;
                break;
            case 2:
                val = FIX.code;
                break;
            case 3:
                val = BALANCE_RATIO.code;
                break;
            default:
                val = CUSTOM_RATIO.code;
                break;
        }
        return val;
    }
}
