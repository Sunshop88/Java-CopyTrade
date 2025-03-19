package net.maku.followcom.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 10:07
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardAccountDataVO implements Serializable {

    //劵商
    private  String brokerName;
    //服务器
    private  String server;

    //账号
    private  String account;

    //vps名称
    private  String vpsName;
    //信号源服务器
    private String sourceServer;
    //信号源账号
    private String sourceAccount;
    //盈利
    private BigDecimal profit;
    //持仓订单量
    private Integer orderNum;
    //持仓订手数
    private Double lots;
    //可用预付款比例
    private BigDecimal marginProportion;

    private BigDecimal proportion;
   //前端不用后端用
    private Integer type;
    //前端不用后端用
    private Long traderId;


}
