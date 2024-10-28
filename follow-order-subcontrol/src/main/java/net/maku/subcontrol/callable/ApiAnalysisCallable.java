package net.maku.subcontrol.callable;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.pojo.SynInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * mt api历史订单同步
 * @author samson bruce
 */
@Data
@Slf4j
public class ApiAnalysisCallable implements Callable<Boolean> {
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    private FollowTraderEntity trader;
    private AbstractApiTrader abstract4ApiTrader;
    private SynInfo synInfo;


    public ApiAnalysisCallable(FollowTraderEntity trader, AbstractApiTrader abstract4ApiTrader, SynInfo synInfo) {
        this.scheduledThreadPoolExecutor = ThreadPoolUtils.getScheduledExecute();
        this.trader = trader;
        this.abstract4ApiTrader = abstract4ApiTrader;
        this.synInfo = synInfo;
    }

    @Override
    public Boolean call() throws Exception {
        Boolean call;
        try {
//            new Analysis(SpringContextUtils.getBean(AotfxAnalysisConsecutiveDataDayServiceImpl.class)).work(abstract4ApiTrader,trader,synInfo.getRemoveAll());
            call = Boolean.TRUE;
        } catch (Exception e) {
            call = Boolean.FALSE;
        } finally {
//            boolean remove = this.scheduledThreadPoolExecutor.running.remove(trader.getId());
        }
        return call;
    }
}
