package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 18:07
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

    /**
     * 净头寸
     */

    private BigDecimal position;

    /**
     * 总持仓手数
     */

    private BigDecimal lots;

    /**
     * 总订单数
     */

    private BigDecimal num;

    /**
     * 总盈利
     */

    private BigDecimal profit;

    List<FollowTraderAnalysisEntity>  symbolAnalysisDetails;
}
