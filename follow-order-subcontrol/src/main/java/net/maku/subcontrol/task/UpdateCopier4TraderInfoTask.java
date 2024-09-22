package net.maku.subcontrol.task;

import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.subcontrol.util.SpringContextUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author samson bruce
 */
public class UpdateCopier4TraderInfoTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UpdateCopier4TraderInfoTask.class);
    AbstractApiTrader abstractApiTrader;
    FollowTraderEntity copier;

    FollowTraderService traderService;

    public UpdateCopier4TraderInfoTask(AbstractApiTrader abstractApiTrader) {
        this.abstractApiTrader = abstractApiTrader;
        this.copier = abstractApiTrader.getTrader();
        traderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
    }

    @Override
    public void run() {
        try {
//            FollowTraderEntity traderFromDb = traderService.getById(copier.getId());
//            Integer isProtectEquity = traderFromDb.getIsProtectEquity();
//            if (isProtectEquity == 1) {
//                abstractApiTrader.riskControl(traderFromDb);
//            }
//           更新前后缀和账号信息
            abstractApiTrader.updateTraderInfo();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
