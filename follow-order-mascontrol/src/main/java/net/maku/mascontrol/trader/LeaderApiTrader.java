package net.maku.mascontrol.trader;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.mascontrol.even.OnQuoteHandler;

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

    public LeaderApiTrader(FollowTraderEntity trader, String host, int port, LocalDateTime closedOrdersFrom, LocalDateTime closedOrdersTo) throws IOException {
        super(trader, host, port, closedOrdersFrom, closedOrdersTo);
    }

    @Override
    public void connect2Broker() throws Exception {
        super.connect2Broker();
    }

    public void startTrade() {
        super.updateTradeInfoFuture = this.scheduledExecutorService.scheduleWithFixedDelay(new UpdateLeaderTraderInfoTask(this), 6, 10, TimeUnit.SECONDS);
    }

    @Override
    public void addOnQuoteHandler(OnQuoteHandler onQuoteHandler) {
        super.addOnQuoteHandler(onQuoteHandler);
    }

    public OnQuoteHandler getQuoteHandler() {
        return super.getQuoteHandler();
    }

    @Override
    public void removeOnQuoteHandler() {
        super.removeOnQuoteHandler();
    }
}
