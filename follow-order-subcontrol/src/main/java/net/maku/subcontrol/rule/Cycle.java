package net.maku.subcontrol.rule;


import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
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
    protected PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, CopierApiTrader copier4ApiTrader) {
        return getPermitInfo(leaderCopier, orderInfo);
    }

//    @Override
//    protected PermitInfo permit(EaLeaderCopier eaLeaderCopier, EaOrderInfo eaOrderInfo, Copier5ApiTrader copier5ApiTrader) {
//        return getPermitInfo(eaLeaderCopier, eaOrderInfo);
//    }

    @NotNull
    private PermitInfo getPermitInfo(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo) {
        PermitInfo permitInfo;
//        String comment = eaOrderInfo.getComment();
//        int indexOf = comment.indexOf("#");
//        int i = comment.indexOf("#", indexOf + 1);
//        int lastIndexOf = comment.lastIndexOf("#");
//        int cycle = Integer.parseInt(comment.substring(i + 1, lastIndexOf));
//        if (!(cycle >= eaLeaderCopier.getStartCycle() && cycle <= eaLeaderCopier.getStopCycle())) {
//            permitInfo = new PermitInfo();
//            permitInfo.setPermitted(Boolean.FALSE);
//            return permitInfo;
//        }

        permitInfo = new PermitInfo();
        permitInfo.setPermitted(Boolean.TRUE);
        return permitInfo;
    }
}
