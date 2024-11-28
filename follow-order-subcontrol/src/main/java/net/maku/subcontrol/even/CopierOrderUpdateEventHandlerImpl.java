package net.maku.subcontrol.even;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.subcontrol.trader.AbstractApiTrader;
import online.mtapi.mt4.OrderUpdateEventArgs;

import java.util.Date;
import java.util.List;
/**
 * @author Samson Bruce
 */
@Slf4j
public class CopierOrderUpdateEventHandlerImpl extends OrderUpdateHandler {
    AbstractApiTrader copier4ApiTrader;

    // 上次执行时间
    private long lastInvokeTime = 0;

    // 设定时间间隔，单位为毫秒
    private final long interval = 1000; // 1秒间隔

    public CopierOrderUpdateEventHandlerImpl(AbstractApiTrader abstract4ApiTrader) {
        super();
        this.copier4ApiTrader = abstract4ApiTrader;
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
            // 获取该symbol上次执行时间
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastInvokeTime >= interval) {
                //查看喊单信息
                List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, copier4ApiTrader.getTrader().getId()));
                list.forEach(o -> {
                    //发送消息
                    // 判断当前时间与上次执行时间的间隔是否达到设定的间隔时间
                    // 获取当前系统时间
                    lastInvokeTime = currentTime;
                    //发送消息
                    traderOrderActiveWebSocket.sendPeriodicMessage(o.getMasterId().toString(), leader.getId().toString());
                });
            }
        }
    }
}
