package com.flink.dwd.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Author:  zsd
 * Date:  2025/1/15/周三 16:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderActiveInfoVO implements Serializable {

    //订单号
    private Integer orderNo;

    //账号
    private String account;

    //开仓时间
    private LocalDateTime openTime;

    //品种
    private String symbol;

    //手数
    private double lots;

    //开仓价格
    private double openPrice;

    //止盈
    private double takeProfit;

    //止损
    private double stopLoss;

    //手续费
    private double commission;

    //利息
    private double swap;

    //盈亏
    private double profit;

    //下单类型
    private String type;

    //魔术号
    private Integer magicNumber;

    //备注
    private String comment;

}