package net.maku.followcom.enums;

/**
 * Author:  zsd
 * Date:  2025/2/13/周四 10:36
 */
public enum PlacedTypeEnum {

    //跟随模式0-固定手数 1-手数比例 2-净值比例
    CLIENT(0, 0, "Client"),
    EXPERT(1, 7, "Expert"),
    DEALER(2, 1, "Dealer"),
    SIGNAL(3, 2, "Signal"),
    GATEWAY(4, 3, "Gateway"),
    MOBILE(5, 4, "Mobile"),
    WEB(6, 5, "Web"),
    API(7, 6, "Api"),
    DEFAULT(8, 8, "Default");

    private final Integer code;

    private final Integer apiCode;
    private final String description;

    PlacedTypeEnum( Integer apiCode, Integer code,String description) {
        this.apiCode = apiCode;
        this.code = code;
        this.description = description;
    }
    public static Integer getApiCode(Integer code) {
        Integer val = CLIENT.code;
        switch (code) {
            case 0:
                val = CLIENT.apiCode;
                break;
            case 1:
                val = DEALER.code;
                break;
            case 2:
                val = SIGNAL.code;
                break;
            case 3:
                val = GATEWAY.code;
                break;
            case 4:
                val = MOBILE.code;
                break;
            case 5:
                val = WEB.code;
                break;
            case 6:
                val = API.code;
                break;
            case 7:
                val = EXPERT.code;
                break;
            case 8:
                val = DEFAULT.code;
                break;
            default:
                val = DEFAULT.code;
                break;
        }
        return val;
    }

    public static String getDesc(Integer code) {
        String val = CLIENT.description;
        switch (code) {
            case 0:
                val = CLIENT.description;
                break;
            case 1:
                val = DEALER.description;
                break;
            case 2:
                val = SIGNAL.description;
                break;
            case 3:
                val = GATEWAY.description;
                break;
            case 4:
                val = MOBILE.description;
                break;
            case 5:
                val = WEB.description;
                break;
            case 6:
                val = API.description;
                break;
            case 7:
                val = EXPERT.description;
                break;
            case 8:
                val = DEFAULT.description;
                break;
            default:
                val = DEFAULT.description;
                break;
        }
        return val;
    }
    public static Integer getVal(Integer apiCode) {
        Integer val = CLIENT.code;
        switch (apiCode) {
            case 0:
                val = CLIENT.code;
                break;
            case 1:
                val = EXPERT.code;
                break;
            case 2:
                val = DEALER.code;
                break;
            case 3:
                val = SIGNAL.code;
                break;
            case 4:
                val = GATEWAY.code;
                break;
            case 5:
                val = MOBILE.code;
                break;
            case 6:
                val = WEB.code;
                break;
            case 7:
                val = API.code;
                break;
            case 8:
                val = DEFAULT.code;
                break;
            default:
                val = DEFAULT.code;
                break;
        }
        return val;
    }




}
