package net.maku.subcontrol.trader;

import lombok.Data;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import online.mtapi.mt4.Order;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// 订单发送成功事件
@Data
public class OrderResultEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Order order;
    private final EaOrderInfo orderInfo;
    private final FollowSubscribeOrderEntity openOrderMapping;
    private final FollowTraderEntity copier;
    private final Integer flag;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final double startPrice;
    private final String ipAddress;
    private final BigDecimal priceSlip;

    public OrderResultEvent(Order order, EaOrderInfo orderInfo,
                            FollowSubscribeOrderEntity openOrderMapping,
                            FollowTraderEntity copier, Integer flag, LocalDateTime startTime, LocalDateTime endTime, double startPrice, String ipAddress,BigDecimal priceSlip) {
        this.order = order;
        this.orderInfo = orderInfo;
        this.openOrderMapping = openOrderMapping;
        this.copier = copier;
        this.flag = flag;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startPrice = startPrice;
        this.ipAddress = ipAddress;
        this.priceSlip=priceSlip;
    }

    // Getters
}