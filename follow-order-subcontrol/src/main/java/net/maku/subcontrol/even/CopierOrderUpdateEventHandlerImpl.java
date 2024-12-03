package net.maku.subcontrol.even;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.impl.FollowOrderHistoryServiceImpl;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.OrderUpdateEventArgs;

import java.util.List;

/**
 * @author Samson Bruce
 */
@Slf4j
public class CopierOrderUpdateEventHandlerImpl extends OrderUpdateHandler {
    AbstractApiTrader copier4ApiTrader;
    protected FollowOrderHistoryService followOrderHistoryService;

    // 上次执行时间
    private long lastInvokeTime = 0;

    // 设定时间间隔，单位为毫秒
    private final long interval = 1000; // 1秒间隔

    public CopierOrderUpdateEventHandlerImpl(AbstractApiTrader abstract4ApiTrader) {
        super();
        this.copier4ApiTrader = abstract4ApiTrader;
        this.followOrderHistoryService = SpringContextUtils.getBean(FollowOrderHistoryServiceImpl.class);
    }

    @Override
    public void invoke(Object sender, OrderUpdateEventArgs orderUpdateEventArgs) {
        int flag = 0;
        try {
            //发送websocket消息标识
            switch (orderUpdateEventArgs.Action) {
                case PositionOpen:
                case PendingFill:
                case PositionClose:
                    flag = 1;
                    break;
                default:
                    log.error("Unexpected value: " + orderUpdateEventArgs.Action);
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        if (flag == 1) {
            //查看喊单信息
            List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, copier4ApiTrader.getTrader().getId()));
            list.forEach(o -> {
                //发送消息
                log.info("跟单websocket" + copier4ApiTrader.getTrader().getId());
                traderOrderActiveWebSocket.sendPeriodicMessage(o.getMasterId().toString(), copier4ApiTrader.getTrader().getId().toString());
            });
            //保存历史数据
            followOrderHistoryService.saveOrderHistory(copier4ApiTrader.quoteClient, copier4ApiTrader.getTrader());
        }
    }


}
