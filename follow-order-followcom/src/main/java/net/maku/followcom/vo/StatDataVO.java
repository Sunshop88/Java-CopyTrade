package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 17:12
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatDataVO {
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
     * vps数量
     */
    private Integer vpsNum;

    /**
     * 信号源数量
     */
    private Integer sourceNum;

    /**
     * 跟单数量数量
     */
    private Integer followNum;
}
