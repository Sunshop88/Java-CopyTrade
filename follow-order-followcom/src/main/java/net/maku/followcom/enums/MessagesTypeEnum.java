package net.maku.followcom.enums;

/**
 * Author:  zsd
 * Date:  2025/1/23/周四 9:41
 */
public enum MessagesTypeEnum {
    MISSING_ORDERS_NOTICE(0, "漏单通知"),
    MISSING_ORDERS_INSPECT(1, "定时漏单检查");
    private int code;
    private String desc;
    private MessagesTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }
}
