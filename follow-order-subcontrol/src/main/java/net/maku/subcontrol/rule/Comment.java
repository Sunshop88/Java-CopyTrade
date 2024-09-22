package net.maku.subcontrol.rule;


import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.subcontrol.pojo.EaOrderInfo;
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
    protected PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, CopierApiTrader copier4ApiTrader) {
        return getPermitInfo(leaderCopier, orderInfo);
    }

//    @Override
//    protected PermitInfo permit(AotfxMasterSlave leaderCopier, EaOrderInfo orderInfo, Copier5ApiTrader copier5ApiTrader) {
////        if (orderInfo.getComment().startsWith(FROMSHARP) && orderInfo.getPlatform() == MetaTraderEnum.MT4.getValue()) {
////            PermitInfo permitInfo = new PermitInfo();
////            permitInfo.setPermitted(Boolean.FALSE);
////            permitInfo.setExtra("MT5不处理MT4的部分平仓触发的开仓信号");
////            return permitInfo;
////        }
//        return getPermitInfo(leaderCopier, orderInfo);
//    }

    @NotNull
    private PermitInfo getPermitInfo(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo) {
        PermitInfo permitInfo=new PermitInfo();
//        String orderInfoComment = eaOrderInfo.getComment() == null ? "" : eaOrderInfo.getComment();
//        String[] followComments = eaLeaderCopier.getComment()==null ? "".split("@") : eaLeaderCopier.getComment().split("@");
//        String[] unfollowComments = eaLeaderCopier.getComment()==null ? "".split("@") : eaLeaderCopier.getComment().split("@");
//
//        if (FOLLOW.equalsIgnoreCase(eaLeaderCopier.getCommentRule()) && followComments.length != 0) {
//            List<String> follows = Arrays.stream(followComments).filter(item -> !ObjectUtils.isEmpty(item)).collect(Collectors.toList());
//            if (follows.size() != 0) {
//                for (String comment : follows) {
//                    if (orderInfoComment.contains(comment)) {
//                        permitInfo = new PermitInfo();
//                        permitInfo.setPermitted(Boolean.TRUE);
//                        return permitInfo;
//                    }
//                }
//                permitInfo = new PermitInfo(Boolean.FALSE, -8, "没有订阅该COMMENT", 0);
//                return permitInfo;
//            }
//
//        } else if (UNFOLLOW.equalsIgnoreCase(eaLeaderCopier.getCommentRule()) && unfollowComments.length != 0) {
//            List<String> unfollows = Arrays.stream(unfollowComments).filter(item -> !ObjectUtils.isEmpty(item)).collect(Collectors.toList());
//            for (String comment : unfollows) {
//                if (orderInfoComment.contains(comment)) {
//                    permitInfo = new PermitInfo(Boolean.FALSE, -9, "不订阅该COMMENT", 0);
//                    return permitInfo;
//                }
//            }
//            permitInfo = new PermitInfo();
//            permitInfo.setPermitted(Boolean.TRUE);
//            return permitInfo;
//        }
//        permitInfo = new PermitInfo();
        permitInfo.setPermitted(Boolean.TRUE);
        return permitInfo;
    }

}
