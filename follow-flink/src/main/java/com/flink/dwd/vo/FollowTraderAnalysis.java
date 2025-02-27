package com.flink.dwd.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2025/1/6/周一 15:32
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowTraderAnalysis  {
    /**
     * mt4账号
     */
    private Integer account;

    /**
     * vpsId
     */
    private Integer vpsId;

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

    private Double position;

    /**
     * 总持仓手数
     */

    private Double lots;

    /**
     * 总订单数
     */
    private Integer num;

    /**
     * 总盈利
     */
    private Double profit;
    private  Double  equity;

    /**
     * 多仓订单量
     */

    private Integer buyNum;

    /**
     * 多仓手数
     */
    private Double buyLots;

    /**
     * 多仓盈利
     */
    private Double buyProfit;

    /**
     * 空仓订单量
     */
    private Integer sellNum;

    /**
     * 空仓手数
     */
    private Double sellLots;

    /**
     * 空仓盈利
     */
    private Double sellProfit;
    /**
     * 账号类型
     */
    private Integer type;
    /**
     * 可用预付款比例
     * */
    private Double freeMargin;
}
