package net.maku.subcontrol.even;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.AcEnum;
import net.maku.followcom.enums.OrderChangeTypeEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.impl.FollowTraderSubscribeServiceImpl;
import net.maku.followcom.vo.OrderCacheVO;
import net.maku.followcom.util.SpringContextUtils;
//import net.maku.subcontrol.config.RabbitMQProducer;
import net.maku.subcontrol.config.RabbitMQProducer;
import net.maku.subcontrol.entity.Account;
import net.maku.subcontrol.entity.MessagePayload;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.websocket.TraderOrderActiveWebSocket;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.OrderUpdateEventArgs;
import online.mtapi.mt4.OrderUpdateEventHandler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * @author samson bruce
 */
@Slf4j
@Data
public class OrderUpdateHandler implements OrderUpdateEventHandler {

    protected String topic;
    protected FollowTraderEntity leader;
    protected AbstractApiTrader abstractApiTrader;

    protected FollowSubscribeOrderService followSubscribeOrderService;
    protected FollowTraderSubscribeService followTraderSubscribeService;

    protected Boolean running = Boolean.TRUE;
    protected TraderOrderActiveWebSocket traderOrderActiveWebSocket;
    protected RabbitMQProducer producer;

    public OrderUpdateHandler() {
        this.followSubscribeOrderService = SpringContextUtils.getBean(FollowSubscribeOrderService.class);
        this.traderOrderActiveWebSocket=SpringContextUtils.getBean(TraderOrderActiveWebSocket .class);
        this.followTraderSubscribeService=SpringContextUtils.getBean(FollowTraderSubscribeServiceImpl.class);
        this.producer=SpringContextUtils.getBean(RabbitMQProducer.class);
    }

    /**
     * 向消费主题，发送交易信号，供MT4MT5跟单者消费。
     *
     * @param type         交易类型
     * @param order        订单信息
     * @param equity       喊单者的净值
     * @param currency     喊单者的存款货币
     * @param detectedDate 侦测到交易动作的时间
     */
    protected EaOrderInfo send2Copiers(OrderChangeTypeEnum type, online.mtapi.mt4.Order order, double equity, String currency, LocalDateTime detectedDate) {

        // 并且要给EaOrderInfo添加额外的信息：喊单者id+喊单者账号+喊单者服务器
        // #84 喊单者发送订单前需要处理前后缀
        EaOrderInfo orderInfo = new EaOrderInfo(order, leader.getId() ,leader.getAccount(), leader.getServerName(), equity, currency, Boolean.FALSE);
        assembleOrderInfo(type, orderInfo, detectedDate);
        return orderInfo;
    }


    void assembleOrderInfo(OrderChangeTypeEnum type, EaOrderInfo orderInfo, LocalDateTime detectedDate) {
        if (type == OrderChangeTypeEnum.NEW) {
            orderInfo.setOriginal(AcEnum.MO);
            orderInfo.setDetectedOpenTime(detectedDate);
        } else if (type == OrderChangeTypeEnum.CLOSED) {
            orderInfo.setDetectedCloseTime(detectedDate);
            orderInfo.setOriginal(AcEnum.MC);
        } else if (type == OrderChangeTypeEnum.MODIFIED) {
            orderInfo.setOriginal(AcEnum.MM);
        }
    }


    /**
     * 后续可以屏蔽该函数
     *
     * @param openTime  订单开仓时间
     * @param closeTime 订单平仓时间
     * @return milliseconds
     */
    protected int delaySendCloseSignal(LocalDateTime openTime, LocalDateTime closeTime) {
        // 开仓后立刻平仓，容易出现一个现象就是跟单者新开订单还没开出来，平仓信号到了后，会出现平仓失败。
        // 虽然最后循环平仓也会成功，但是会做一个优化就是开平仓时间间隔很短的平仓信号都做一个延迟。实际的交易很少会出现这种情况，只是做一个优化。
        try {
            Duration duration = Duration.between(openTime, closeTime).abs();
            int milliseconds = 2000;
            if (duration.get(ChronoUnit.MILLIS) < milliseconds) {
                return 1000;
            } else {
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void invoke(Object o, OrderUpdateEventArgs orderUpdateEventArgs) {

    }

    protected MessagePayload getMessagePayload(Order x) {
        MessagePayload messagePayload = new MessagePayload();
        messagePayload.setAccount(Account.builder().id(leader.getId()).type(leader.getType()).build());
        messagePayload.setUser(Long.parseLong(leader.getAccount()));
        OrderCacheVO orderCacheVO = new OrderCacheVO();
        orderCacheVO.setTicket(x.Ticket);
        orderCacheVO.setOpenTime(x.OpenTime);
        orderCacheVO.setCloseTime(x.CloseTime);
        orderCacheVO.setType(x.Type);
        orderCacheVO.setLots(x.Lots);
        orderCacheVO.setSymbol(x.Symbol);
        orderCacheVO.setOpenPrice(x.OpenPrice);
        orderCacheVO.setStopLoss(x.StopLoss);
        orderCacheVO.setTakeProfit(x.TakeProfit);
        orderCacheVO.setClosePrice(x.ClosePrice);
        orderCacheVO.setMagicNumber(x.MagicNumber);
        orderCacheVO.setSwap(x.Swap);
        orderCacheVO.setCommission(x.Commission);
        orderCacheVO.setComment(x.Comment);
        orderCacheVO.setProfit(x.Profit);
        orderCacheVO.setPlaceType("Client");
        orderCacheVO.setLogin(Long.parseLong(leader.getAccount()));
        messagePayload.setOrder(orderCacheVO);
        return messagePayload;
    }

}
