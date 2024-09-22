package net.maku.subcontrol.task;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.maku.subcontrol.pojo.LiveOrderInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Shaozz
 * @date 2022/8/25 16:37
 */
@Slf4j
public class SyncLiveOrdersTask {

    private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(100);


    private final AbstractApiTrader abstract4ApiTrader;
    private ScheduledFuture<?> scheduledFuture = null;

    @Getter
    private List<LiveOrderInfo> liveOrders = new ArrayList<>();


    public SyncLiveOrdersTask(AbstractApiTrader abstract4ApiTrader) {
        this.abstract4ApiTrader = abstract4ApiTrader;
    }

    public void start() {
        //开启定时任务，暂定5s/次
        if (this.abstract4ApiTrader != null) {
            scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> liveOrders = Arrays.stream(abstract4ApiTrader.quoteClient.GetOpenedOrders()).filter(order -> order.Lots > 0.0).map(LiveOrderInfo::new).collect(Collectors.toList()), 0, 5, TimeUnit.SECONDS);
        }
    }

    public void cancel() {
        scheduledFuture.cancel(true);
        scheduledFuture = null;
    }
}
