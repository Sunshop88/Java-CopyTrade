package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2025/1/10/周五 19:46
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowTraderAnalysisEntityVO {
    /**
     * mt4账号
     */

    private String account;

    /**
     * vpsId
     */

    private Long vpsId;

    /**
     * 品种
     */

    private String symbol;

    /**
     * vps名称
     */

    private String vpsName;

    /**
     * 平台服务器
     */

    private String platform;

    /**
     * 平台服务器id
     */

    private Integer platformId;

    /**
     * 信号源账号
     */

    private String sourceAccount;

    /**
     * 信号源服务器
     */

    private String sourcePlatform;

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
    /**
     * 账号类型
     */
    private Integer type;
}
