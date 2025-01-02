package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 18:07
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SymbolChartVO {
    /**
     * 品种
     */
    private String symbol;
    /**
     * buy订单量
     */
    private BigDecimal buyNum;

    /**
     * buy手数
     */
    private BigDecimal buyLots;

    /**
     * buy盈利
     */
    private BigDecimal buyProfit;

    /**
     * sell订单量
     */
    private BigDecimal sellNum;

    /**
     * sell手数
     */
    private BigDecimal sellLots;

    /**
     * sell盈利
     */
    private BigDecimal sellProfit;
}
