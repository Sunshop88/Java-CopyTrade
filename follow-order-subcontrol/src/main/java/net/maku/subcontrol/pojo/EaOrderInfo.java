package net.maku.subcontrol.pojo;


import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.enums.AcEnum;
import net.maku.subcontrol.trader.AbstractApiTrader;
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

    private long ticket;
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
    private String masterId;
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
     * 用户循环开仓
     */
    private String eaOrderOpenMappingId;


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

    public EaOrderInfo(online.mtapi.mt4.Order order, String masterId, String account, String server, double equity, String currency, boolean arrears) {
        this(order);
        this.masterId = masterId;
        this.account = account;
        this.server = server;
        this.equity = equity;
        this.currency = currency;
        this.arrears = arrears;
    }

    public void removeSymbolPrefixSuffix(List<String> prefixSuffixList, Map<String, String> modSymbolMap) {
        // 强制修正,将品种名映射成逻辑品种名，如：将GOLD映射成"黄金"
        String logicSymbol = modSymbolMap.get(this.symbol);
        if (!ObjectUtils.isEmpty(logicSymbol)) {
            this.symbol = logicSymbol;
        } else {
            //如果强制映射不存在则进行前后缀删除
            for (String prefixSuffixItem : prefixSuffixList) {
                if (!ObjectUtils.isEmpty(prefixSuffixItem)) {
                    String[] prefixSuffix = prefixSuffixItem.split(",");
                    String prefix = Objects.equals(prefixSuffix[0], AbstractApiTrader.EMPTY) ? StrUtil.EMPTY : prefixSuffix[0];
                    String suffix = Objects.equals(prefixSuffix[1], AbstractApiTrader.EMPTY) ? StrUtil.EMPTY : prefixSuffix[1];
                    if (!ObjectUtils.isEmpty(prefix) || !ObjectUtils.isEmpty(suffix)) {
                        if (this.symbol.startsWith(prefix) && this.symbol.endsWith(suffix)) {
                            //删除前缀
                            this.symbol = this.symbol.replaceFirst(prefix, StrUtil.EMPTY);
                            //删除后缀
                            int lastIndexOf = this.symbol.lastIndexOf(suffix);
                            if (lastIndexOf != -1) {
                                this.symbol = this.symbol.substring(0, lastIndexOf);
                            }
                        }
                    }
                }
            }
        }
        this.removedSymbol = this.symbol;
    }


    /**
     * 跟单者补全前后缀
     *
     * @param modSymbolMap     修正映射
     * @param prefixSuffixList 前后对配对组
     */
    public void addSymbolPrefixSuffix(Map<String, String> modSymbolMap, List<String> prefixSuffixList) {

        String modSymbol = modSymbolMap.get(this.symbol);
        if (!ObjectUtils.isEmpty(modSymbol)) {
            //跟单者使用的时候映射的是原始品种名
            Arrays.stream(modSymbol.split(","))
                    .forEach(ms -> {
                        if (ms.contains(this.removedSymbol)) {
                            symbolList.add(0, ms);
                        } else {
                            symbolList.add(ms);
                        }
                    });
        } else {
            //前后缀补全的整体思路是，将多组前后缀都补全，进行返回；由后续开仓环节多多个品种进行开仓；
            for (String prefixSuffix : prefixSuffixList) {
                StringBuilder symbolBuffer = new StringBuilder();
                if (!ObjectUtils.isEmpty(prefixSuffixList)) {
                    String[] ps = prefixSuffix.split(StrUtil.COMMA);
                    if (ObjectUtils.isEmpty(ps)) {
                        symbolBuffer.append(this.symbol);
                    } else {
                        symbolBuffer.append(Objects.equals(ps[0], "empty") ? StrUtil.EMPTY : ps[0]).append(this.symbol).append(Objects.equals(ps[1], "empty") ? StrUtil.EMPTY : ps[1]);
                    }
                    if (oriSymbol.equalsIgnoreCase(symbolBuffer.toString())) {
                        symbolList.add(0, symbolBuffer.toString());
                    } else {
                        symbolList.add(symbolBuffer.toString());
                    }
                }
            }
            //解决有前后缀账户的没有前后缀部分的开仓
            boolean anyMatch = symbolList.stream().anyMatch(symbol -> StrUtil.containsAnyIgnoreCase(symbol, this.oriSymbol));
            if (!anyMatch) {
                symbolList.add(this.oriSymbol);
            }
        }
    }

}
