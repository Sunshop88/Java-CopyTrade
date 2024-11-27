package net.maku.followcom.pojo;


import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.enums.AcEnum;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.springframework.util.ObjectUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 用户自定义的订单信息，
 * 同时解决了跟单时，不同的经纪商服务器之间货币对不一致的情况。
 * 此类问题包含两个方面。
 * <p>
 * 一类是：前后缀的问题，如：万致的服务器货币对都有一个-STD的后缀。而跟单者的服务器货币没有-STD的货币对。
 * </p>
 * <p>
 * 二类是：货币对完全不一致的情况，这类主要存在于黄金、原油如：有些平台的黄金是XAUUSD，而有些平台的黄金GOLD。
 * </p>
 * <p>
 * 系统将第一类问题定义为前后缀修正，第二位定义为强制修正。
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class EaOrderInfo implements Serializable {

    private Integer ticket;
    private int type;
    private Op orderType;
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
    private BigDecimal profit;
    private BigDecimal commission;
    private BigDecimal swap;
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
    private AcEnum original = AcEnum.MO;
    /**
     * 4-MT4 5-MT5
     */
    private int platform = 4;

    /**
     * 跟单注释
     */
    private String slaveComment;

    private String slaveId;


    public EaOrderInfo(Order order) {
        if (order != null) {
            this.ticket = order.Ticket;
            this.type = order.Type.getValue();
            this.orderType = null;
            this.openTime = order.OpenTime;
            this.closeTime = order.CloseTime;
            this.magic = order.MagicNumber;
            this.expiration = order.Expiration;
            this.lots = order.Lots;
            this.openPrice = order.OpenPrice;
            this.closePrice = order.ClosePrice;
            this.sl = order.StopLoss;
            this.tp = order.TakeProfit;
            this.profit = BigDecimal.valueOf(order.Profit);
            this.commission = BigDecimal.valueOf(order.Commission);
            this.swap = BigDecimal.valueOf(order.Swap);
            this.symbol = order.Symbol;
            this.comment = order.Comment;
            this.oriSymbol = order.Symbol;
            this.platform = 4;
            this.deleteFlag = 0;
        }
    }

    public EaOrderInfo(Order order, Long masterId, String account, String server, double equity, String currency, boolean arrears) {
        this(order);
        this.masterId = masterId;
        this.account = account;
        this.server = server;
        this.equity = equity;
        this.currency = currency;
        this.arrears = arrears;
    }

    // 自定义构造函数用于字段校验
    public static EaOrderInfo fromJSON(String json) {
        // 处理嵌套 JSON 字符串
        if (json.startsWith("\"") && json.endsWith("\"")) {
            json = json.substring(1, json.length() - 1).replace("\\\"", "\"");
        }

        EaOrderInfo info = JSON.parseObject(json, EaOrderInfo.class);

        // 初始化 BigDecimal 字段
        if (info.getProfit() == null) info.setProfit(BigDecimal.ZERO);
        if (info.getCommission() == null) info.setCommission(BigDecimal.ZERO);
        if (info.getSwap() == null) info.setSwap(BigDecimal.ZERO);
        info.setOrderType(Op.forValue(info.getType()));
        return info;
    }
}
