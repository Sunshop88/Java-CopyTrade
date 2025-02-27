package com.flink.dwd.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/1/15/周三 16:19
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class EaOrderInfo implements Serializable {

    private Integer ticket;
    private int type;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private long magic;
    private LocalDateTime expiration;
    private double lots;
    private double closeVolume;
    private double openPrice;
    private double closePrice;
    private double sl;
    private double tp;
    private String profit;

    private String swap;
    private Integer deleteFlag;


    /**
     * 订单发生时，mt4的净值
     */
    private double equity;

    /**
     * 订单发生时，喊单者的结算货币
     */
    private String currency;

    /**
     * 喊单者原始的货币对
     */
    private String oriSymbol;

    /**
     * 喊单者删除前后缀的货币对
     */
    private String removedSymbol;

    /**
     * 跟单者备选品种
     */
    private List<String> symbolList = new LinkedList<>();

    /**
     * 跟单者修正后的货币对
     */
    private String symbol;

    private String comment;
    private long diffBitMap;

    /**
     * 喊单着是否欠费
     */
    private boolean arrears;

    /**
     * 喊单者的订单信息
     */
    private Long masterId;
   private Integer serverId;
    private String server;
    private String account;

    /**
     * 侦测到的开平仓时间和时延
     */
    private LocalDateTime detectedOpenTime;
    private int detectedOpenLag;
    /**
     * 跟单者收到的开仓时间
     */
    private LocalDateTime slaveReceiveOpenTime;

    private LocalDateTime detectedCloseTime;
    private int detectedCloseLag;
    /**
     * 跟单者收到的平仓时间
     */
    private LocalDateTime slaveReceiveCloseTime;

    /**
     * 4-MT4 5-MT5
     */
    private int platform = 4;

    /**
     * 跟单注释
     */
    private String slaveComment;

    private String slaveId;
    private Integer placeType;
    private String slaveAccount;
    private String redisKey;

}