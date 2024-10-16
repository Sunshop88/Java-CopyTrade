package net.maku.mascontrol.trader;


import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.util.SpringContextUtils;

/**
 * @author samson bruce
 */
@Slf4j
public class UpdateLeaderTraderInfoTask implements Runnable {
    AbstractApiTrader abstractApiTrader;
    FollowTraderEntity leader;
    FollowTraderService traderService;

    public UpdateLeaderTraderInfoTask(LeaderApiTrader abstractApiTrader) {
        this.abstractApiTrader = abstractApiTrader;
        this.leader = abstractApiTrader.getTrader();
        traderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    }

    @Override
    public void run() {
        try {
            abstractApiTrader.updateTraderInfo();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
