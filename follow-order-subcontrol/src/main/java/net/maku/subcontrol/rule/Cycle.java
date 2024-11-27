package net.maku.subcontrol.rule;


import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTrader;
import org.jetbrains.annotations.NotNull;

public class Cycle extends AbstractFollowRule {

    /**
     * 根据comment判断是否跟随该订单
     *
     * @param leaderCopier   跟随关系
     * @param orderInfo     交易信号
     * @param copier4ApiTrader 跟单者
     * @return PermitInfo
     */
    @Override
    protected PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, AbstractApiTrader copier4ApiTrader) {
        return getPermitInfo(leaderCopier, orderInfo);
    }


    @NotNull
    private PermitInfo getPermitInfo(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo) {
        PermitInfo permitInfo;
        permitInfo = new PermitInfo();
        permitInfo.setPermitted(Boolean.TRUE);
        return permitInfo;
    }
}
