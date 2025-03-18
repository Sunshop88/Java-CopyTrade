package net.maku.subcontrol.trader;

import lombok.Data;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import online.mtapi.mt4.Order;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// 订单发送成功事件
@Data
public class OrderResultCloseEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Order order;
    private final EaOrderInfo orderInfo;
    private final FollowTraderEntity copier;
    private final Integer flag;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final double startPrice;
    private final String ipAddress;
    private final BigDecimal leaderProfit;
    private final BigDecimal copierProfit;
    private final BigDecimal priceSlip;
    public OrderResultCloseEvent(Order order, EaOrderInfo orderInfo,
                                 FollowTraderEntity copier, Integer flag, BigDecimal leaderProfit, BigDecimal copierProfit, LocalDateTime startTime, LocalDateTime endTime, double startPrice, String ipAddress, BigDecimal priceSlip) {
        this.order = order;
        this.orderInfo = orderInfo;
        this.copier = copier;
        this.flag = flag;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startPrice = startPrice;
        this.ipAddress = ipAddress;
        this.leaderProfit = leaderProfit;
        this.copierProfit = copierProfit;
        this.priceSlip = priceSlip;
    }

    // Getters
}