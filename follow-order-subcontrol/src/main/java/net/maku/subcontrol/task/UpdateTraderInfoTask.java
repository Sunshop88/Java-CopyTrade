package net.maku.subcontrol.task;


import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.LeaderApiTrader;

/**
 * @author samson bruce
 */
@Slf4j
public class UpdateTraderInfoTask implements Runnable {
    AbstractApiTrader abstractApiTrader;
    FollowTraderEntity leader;
    FollowTraderService traderService;

    public UpdateTraderInfoTask(AbstractApiTrader abstractApiTrader) {
        this.abstractApiTrader = abstractApiTrader;
        this.leader = abstractApiTrader.getTrader();
        traderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    }

    @Override
    public void run() {
        try {
            if (Thread.currentThread().isInterrupted()) {
                log.info("任务被中断，退出");
                return;
            }
            abstractApiTrader.updateTraderInfo();
        } catch (Exception e) {
            e.printStackTrace();
            Thread.currentThread().interrupt(); // 恢复线程中断状态
        }
    }
}
