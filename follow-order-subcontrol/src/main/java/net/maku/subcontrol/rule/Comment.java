package net.maku.subcontrol.rule;


import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTrader;
import org.jetbrains.annotations.NotNull;
/**
 * @author lee
 */
@Slf4j
public class Comment extends AbstractFollowRule {
    public static String FOLLOW = "FOLLOW";
    public static String UNFOLLOW = "UNFOLLOW";
    public static String NORULE = "NORULE";

    public static String FROMSHARP = "from #";
    public static String toSharpRegex = "^to #[0-9]{4,}$";


    /**
     * 根据comment判断是否跟随该订单
     *
     * @param leaderCopier     跟随关系
     * @param orderInfo        交易信号
     * @param copier4ApiTrader 跟单者
     * @return PermitInfo
     */
    @Override
    protected PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, AbstractApiTrader copier4ApiTrader) {
        return getPermitInfo(leaderCopier, orderInfo);
    }


    @NotNull
    private PermitInfo getPermitInfo(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo) {
        PermitInfo permitInfo=new PermitInfo();
        permitInfo.setPermitted(Boolean.TRUE);
        return permitInfo;
    }

}
