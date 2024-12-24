package net.maku.subcontrol.trader;

import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.task.UpdateTraderInfoTask;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * mtapiMT4跟单对象抽象类
 *
 * @author samson bruce
 */
@Slf4j
public class CopierApiTrader extends AbstractApiTrader {

    public CopierApiTrader(FollowTraderEntity trader,  String host, int port) throws IOException {
        super(trader, host, port);
    }

    public CopierApiTrader(FollowTraderEntity trader,String host, int port, LocalDateTime closedOrdersFrom, LocalDateTime closedOrdersTo) throws IOException {
        super(trader, host, port, closedOrdersFrom, closedOrdersTo);
    }

    @Override
    public void connect2Broker()  throws Exception{
        try {
            super.connect2Broker();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    public Boolean startTrade(List<String> subscriptions) {
//        return startTrade(subscriptions);
//    }

    public Boolean startTrade() {
        return true;
    }

}
