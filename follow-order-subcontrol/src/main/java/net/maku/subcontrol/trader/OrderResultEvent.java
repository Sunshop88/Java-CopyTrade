package net.maku.subcontrol.trader;

import lombok.Data;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import online.mtapi.mt4.Order;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;

// 订单发送成功事件
@Data
public class OrderResultEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Order order;
    private final EaOrderInfo orderInfo;
    private final FollowSubscribeOrderEntity openOrderMapping;
    private final FollowTraderEntity copier;
    private final Integer flag;

    public OrderResultEvent(Order order, EaOrderInfo orderInfo,
                            FollowSubscribeOrderEntity openOrderMapping,
                            FollowTraderEntity copier, Integer flag) {
        this.order = order;
        this.orderInfo = orderInfo;
        this.openOrderMapping = openOrderMapping;
        this.copier = copier;
        this.flag = flag;
    }

    // Getters
}