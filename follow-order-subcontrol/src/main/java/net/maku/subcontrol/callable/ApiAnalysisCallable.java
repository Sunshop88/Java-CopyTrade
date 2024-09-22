package net.maku.subcontrol.callable;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.subcontrol.pojo.SynInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.util.ThreeStrategyThreadPoolExecutor;

import java.util.concurrent.Callable;

/**
 * mt api历史订单同步
 * @author samson bruce
 */
@Data
@Slf4j
public class ApiAnalysisCallable implements Callable<Boolean> {
    ThreeStrategyThreadPoolExecutor threeStrategyThreadPoolExecutor;

    private FollowTraderEntity trader;
    private AbstractApiTrader abstract4ApiTrader;
    private SynInfo synInfo;


    public ApiAnalysisCallable(ThreeStrategyThreadPoolExecutor threeStrategyThreadPoolExecutor, FollowTraderEntity trader, AbstractApiTrader abstract4ApiTrader, SynInfo synInfo) {
        this.threeStrategyThreadPoolExecutor = threeStrategyThreadPoolExecutor;
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
            boolean remove = this.threeStrategyThreadPoolExecutor.running.remove(trader.getId());
        }
        return call;
    }
}
