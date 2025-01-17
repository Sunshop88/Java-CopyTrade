package net.maku.subcontrol.even;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.OrderUpdateEventArgs;
import online.mtapi.mt4.UpdateAction;

import java.util.List;
import java.util.Objects;

/**
 * @author Samson Bruce
 */
@Slf4j
public class CopierOrderUpdateEventHandlerImpl extends OrderUpdateHandler {
    AbstractApiTrader copier4ApiTrader;
    protected FollowOrderHistoryService followOrderHistoryService;

    // 设定时间间隔，单位为毫秒
    private final long interval = 1000; // 1秒间隔

    public CopierOrderUpdateEventHandlerImpl(AbstractApiTrader abstract4ApiTrader) {
        super();
        this.leader = abstract4ApiTrader.getTrader();
        this.copier4ApiTrader = abstract4ApiTrader;
        this.followOrderHistoryService = SpringContextUtils.getBean(FollowOrderHistoryServiceImpl.class);
    }

    @Override
    public void invoke(Object sender, OrderUpdateEventArgs orderUpdateEventArgs) {
        try {
            //发送websocket消息标识
//            if (Objects.requireNonNull(orderUpdateEventArgs.Action) == UpdateAction.PositionClose) {
//                Order x = orderUpdateEventArgs.Order;
//                log.info("跟单发送平仓mq" + leader.getId());
//                ThreadPoolUtils.getExecutor().execute(()-> {
//                    //发送平仓MQ
//                    producer.sendMessage(JSONUtil.toJsonStr(getMessagePayload(x)));
//                });
//            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }


}
