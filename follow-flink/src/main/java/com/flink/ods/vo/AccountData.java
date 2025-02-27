package com.flink.ods.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.mtapi.mt4.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/1/3/周五 16:45
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AccountData {
    private  Double   credit;
    private  Double   freeMargin;
    private  Double   equity;
    private  String   host;
    private  Double   profit;

    private  Integer   user;
    private  String   password;
    private  Integer  num;
     private Integer type;
    /**
     * 平台服务器
     */

    private String platform;
    private Integer platformId;

    /**
     * vpsId
     */
    private Integer vpsId;

    /**
     * vps名称
     */

    private String vpsName;

    private List<Order> orders;






}
