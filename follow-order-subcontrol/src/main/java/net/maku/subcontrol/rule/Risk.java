package net.maku.subcontrol.rule;

import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.trader.CopierApiTrader;

/**
 * @author X.T. LI
 */
public class Risk extends AbstractFollowRule {

    @Override
    protected AbstractFollowRule.PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, CopierApiTrader copier4ApiTrader) {

        PermitInfo permitInfo;
        permitInfo = new PermitInfo();
        permitInfo.setPermitted(Boolean.TRUE);
        return permitInfo;
    }
}
