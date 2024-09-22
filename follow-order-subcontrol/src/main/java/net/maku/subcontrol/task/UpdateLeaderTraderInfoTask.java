package net.maku.subcontrol.task;


import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.subcontrol.util.SpringContextUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.LeaderApiTrader;

/**
 * @author samson bruce
 */
@Slf4j
public class UpdateLeaderTraderInfoTask implements Runnable {
    AbstractApiTrader abstractApiTrader;
    FollowTraderEntity leader;
    FollowTraderService traderService;
    int calledTimes = 0;

    static int updateInfoEachTimes = 10;

    public UpdateLeaderTraderInfoTask(LeaderApiTrader abstractApiTrader) {
        this.abstractApiTrader = abstractApiTrader;
        this.leader = abstractApiTrader.getTrader();
        traderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    }

    @Override
    public void run() {
        try {
            log.info("执行喊单+++{}",leader.getAccount());
//            FollowTraderEntity traderFromDb = traderService.getById(leader.getId());
              //更新前后缀和账号信息
              abstractApiTrader.updateTraderInfo();
//            Integer isProtectEquity = traderFromDb.getIsProtectEquity();
//            if (isProtectEquity == 1) {
//                abstract4ApiTrader.riskControl(traderFromDb);
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
