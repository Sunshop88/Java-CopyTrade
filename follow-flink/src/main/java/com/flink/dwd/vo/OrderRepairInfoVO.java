package com.flink.dwd.vo;

/**
 * Author:  zsd
 * Date:  2025/1/15/周三 15:01
 */

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 漏单记录
 */
@Data
public class OrderRepairInfoVO implements Serializable {

    //喊单订单号
    private Integer masterTicket;

    //喊单开仓时间
    private LocalDateTime masterOpenTime;
    //喊单关仓时间
    private LocalDateTime masterCloseTime;
    //喊单开仓价格
    private double masterOpenPrice;
    //喊单品种
    private String masterSymbol;

    //喊单手数
    private double masterLots;

    //喊单盈亏
    private String masterProfit;

    //喊单下单类型
    private String masterType;

    //跟单订单号
    private Integer slaveTicket;

    //跟单开仓时间
    private String slaveOpenTime;

    //跟单关仓时间
    private String slaveCloseTime;
    //跟单账号
    private String slaveAccount;
    //跟单服务器
    private String slavePlatform;
    //跟单品种
    private String slaveSymbol;

    //跟单手数
    private double slaveLots;

    //跟单开仓价格
    private double slaveOpenPrice;

    //跟单盈亏
    private double slaverProfit;

    //跟单下单类型
    private String slaveType;

    //漏单类型0-跟单 1-平仓
    private Integer repairType;

    //喊单者的ID
    private Long masterId;

    //跟单者的ID
    private Long slaveId;

}