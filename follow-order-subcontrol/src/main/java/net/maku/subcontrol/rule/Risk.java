package net.maku.subcontrol.rule;

import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.subcontrol.pojo.EaOrderInfo;
import net.maku.subcontrol.trader.CopierApiTrader;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import java.math.BigDecimal;

/**
 * @author X.T. LI
 */
public class Risk extends AbstractFollowRule {

    @Override
    protected AbstractFollowRule.PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, CopierApiTrader copier4ApiTrader) {

//        FollowTraderEntity follower = copier4ApiTrader.getTrader();
        PermitInfo permitInfo;
        //单次订阅手数大于了最大手数风控，0或者null就是不限制
//        BigDecimal maxRiskSize = follower.getMaxSize() == null ? BigDecimal.ZERO : follower.getMaxSize();
        //该订阅关系设置的最大手数，0或者null就是不限制
//        try {
//            if (maxRiskSize.compareTo(BigDecimal.valueOf(lots)) > 0) {
//                permitInfo = new PermitInfo(Boolean.FALSE, 55, "超出统一最大手数：" + maxRiskSize,0);
//            } else if (maxLots.compareTo(BigDecimal.valueOf(lots)) > 0) {
//                permitInfo = new PermitInfo(Boolean.FALSE, 56, "超出该订阅关系最大手数：" + maxLots,0);
//            } else {
            permitInfo = new PermitInfo();
            permitInfo.setPermitted(Boolean.TRUE);
//            }
//        } catch (InvalidSymbolException e) {
//            permitInfo = new PermitInfo(Boolean.FALSE, 55, orderInfo.getSymbol()+" InvalidSymbolException",0);
//        } catch (ConnectException e) {
//            permitInfo = new PermitInfo(Boolean.FALSE, 55, "ConnectException",0);
//        } catch (TimeoutException e) {
//            permitInfo = new PermitInfo(Boolean.FALSE, 55, "TimeoutException",0);
//        }


        return permitInfo;
    }

//    @Override
//    protected PermitInfo permit(EaLeaderCopier eaLeaderCopier, EaOrderInfo eaOrderInfo, Copier5ApiTrader copier5ApiTrader) {
//        EaTrader copier5 = copier5ApiTrader.getTrader5();
//        PermitInfo permitInfo;
//        //单次订阅手数大于了最大手数风控，0或者null就是不限制
//        BigDecimal maxRiskSize = copier5.getMaxSize() == null ? BigDecimal.ZERO : copier5.getMaxSize();
//        //该订阅关系设置的最大手数，0或者null就是不限制
//        BigDecimal maxLots = eaLeaderCopier.getMaxLots() == null ? BigDecimal.ZERO : BigDecimal.valueOf(eaLeaderCopier.getMaxLots());
//        double lots = 0;
//        try {
//            lots = lots(eaLeaderCopier, eaOrderInfo, copier5ApiTrader);
//        } catch (InvalidSymbolException e) {
//            throw new RuntimeException(e);
//        } catch (ConnectException e) {
//            throw new RuntimeException(e);
//        } catch (TimeoutException e) {
//            throw new RuntimeException(e);
//        }
//
//        if (maxRiskSize.compareTo(BigDecimal.valueOf(lots)) > 0) {
//            permitInfo = new PermitInfo(Boolean.FALSE, 55, "超出统一最大手数：" + maxRiskSize,0);
//        } else if (maxLots.compareTo(BigDecimal.valueOf(lots)) > 0) {
//            permitInfo = new PermitInfo(Boolean.FALSE, 56, "超出该订阅关系最大手数：" + maxLots,0);
//        } else {
//            permitInfo = new PermitInfo();
//            permitInfo.setPermitted(Boolean.TRUE);
//        }
//        return permitInfo;
//    }
}
