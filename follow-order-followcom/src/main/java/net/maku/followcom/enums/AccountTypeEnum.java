package net.maku.followcom.enums;

import lombok.Data;

/**
 * Author:  zsd
 * Date:  2025/3/13/周四 10:11
 */

public enum AccountTypeEnum {
    MT4("0","mt4"),
    MT5("1","mt5");
    private String type;

    private String des;
    private AccountTypeEnum(String type, String des) {
        this.type = type;
        this.des = des;
    }

    public String getType() {
        return type;
    }
}
