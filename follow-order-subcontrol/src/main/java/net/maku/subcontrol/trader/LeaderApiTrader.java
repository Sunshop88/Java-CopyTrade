package net.maku.subcontrol.trader;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.task.UpdateTraderInfoTask;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * MetaTrader 4喊单者对象
 */
@Slf4j
public class LeaderApiTrader extends AbstractApiTrader {
    public LeaderApiTrader(FollowTraderEntity trader, String host, int port) throws IOException {
        super(trader, host, port);
    }

    public LeaderApiTrader(FollowTraderEntity trader,String host, int port, LocalDateTime closedOrdersFrom, LocalDateTime closedOrdersTo) throws IOException {
        super(trader, host, port, closedOrdersFrom, closedOrdersTo);
    }

    @Override
    public void connect2Broker() throws Exception {
        super.connect2Broker();
    }

//    public Boolean startTrade(List<String> subscriptions) {
////        return startTrade( subscriptions);
//    }

    public Boolean startTrade() {
        //建立跟单关系
        super.updateTradeInfoFuture = this.scheduledExecutorService.scheduleWithFixedDelay(new UpdateTraderInfoTask(this), 6, 90, TimeUnit.SECONDS);
        return true;
    }
}
