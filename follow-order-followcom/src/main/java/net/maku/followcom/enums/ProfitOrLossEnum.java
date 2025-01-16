package net.maku.followcom.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProfitOrLossEnum {
    /**
     * 盈利
     */
    Profit(0),
    /**
     * 亏损
     */
    Loss(1);


    private final Integer value;
}
