package net.maku.followcom.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 15:11
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SymbolAnalysisVO {
    /**
     * 品种
     */
  
    private String symbol;



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

    /**
     * 多仓订单量
     */

    private BigDecimal buyNum;

    /**
     * 多仓手数
     */
    private BigDecimal buyLots;

    /**
     * 多仓盈利
     */
    private BigDecimal buyProfit;

    /**
     * 空仓订单量
     */

    private BigDecimal sellNum;

    /**
     * 空仓手数
     */

    private BigDecimal sellLots;

    /**
     * 空仓盈利
     */
    private BigDecimal sellProfit;
}
