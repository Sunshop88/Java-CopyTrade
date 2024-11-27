package net.maku.subcontrol.rule;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.subcontrol.pojo.EaSymbolInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author X.T. LI
 */
@Slf4j
public abstract class AbstractFollowRule {
    protected FixedLot fixedLot = new FixedLot();
    protected FixedRatio fixedRatio = new FixedRatio();
    protected FixedEuqit fixedEuqit = new FixedEuqit();

    /**
     * 链中下一元素
     */
    protected AbstractFollowRule nextRule = null;
    protected LeaderApiTradersAdmin leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);

    public void setNextRule(AbstractFollowRule nextRule) {
        this.nextRule = nextRule;
    }

    public PermitInfo rule(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, AbstractApiTrader copierApiTrader) {
        PermitInfo permitInfo = permit(eaLeaderCopier, eaOrderInfo, copierApiTrader);
        if (permitInfo.getPermitted()) {
            //允许跟随
            if (nextRule != null) {
                //需要进一步判断
                return nextRule.permit(eaLeaderCopier, eaOrderInfo, copierApiTrader);
            } else {
                return permitInfo;
            }
        } else {
            return permitInfo;
        }
    }

    /**
     * 计算跟单者跟随的时候，根据当前订单跟随规则，如果是强制开仓，计算完成后不足最小手数，以最小手数开仓。
     *
     * @param masterSlave     订阅关系
     * @param eaOrderInfo     开仓订单信息
     * @param copierApiTrader 跟单者
     * @return double 跟单者开仓手数
     */
    public double lots(FollowTraderSubscribeEntity masterSlave, EaOrderInfo eaOrderInfo, AbstractApiTrader copierApiTrader) throws InvalidSymbolException, ConnectException, TimeoutException {
        double lots = 0;
        if (ObjectUtils.isEmpty(masterSlave)) {
            return 0.0;
        }
        //喊单者、跟单者的合约大小
        EaSymbolInfo copierSymbolInfo = copierApiTrader.symbolInfo(eaOrderInfo.getSymbol(), true);
        EaSymbolInfo leaderSymbolInfo = null;
        //MT4平台
        LeaderApiTrader LeaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(eaOrderInfo.getMasterId().toString());
        leaderSymbolInfo = LeaderApiTrader.symbolInfo(eaOrderInfo.getOriSymbol(), true);


        log.debug("leaderSymbolInfo {}", leaderSymbolInfo);
        log.debug("copierSymbolInfo {}", copierSymbolInfo);
        switch (masterSlave.getFollowMode()) {
            case 0:
                //按固定手数
                lots = fixedLot.lots(masterSlave, eaOrderInfo, 0.0, null);
                break;
            case 1:
                //按比例跟单
                lots = fixedRatio.lots(masterSlave, eaOrderInfo, 0.0, null);
                break;
            case 2:
                //按净值比例
                lots = fixedEuqit.lots(eaOrderInfo, LeaderApiTrader.quoteClient.Equity, copierApiTrader.quoteClient.Equity);
                break;
            case 3:
                //按资金比例(余额
                lots = fixedEuqit.lots(eaOrderInfo, LeaderApiTrader.quoteClient.Balance, copierApiTrader.quoteClient.Balance);
            default:
                lots = 0.0;
                break;
        }
        BigDecimal lots2digits = BigDecimal.valueOf(lots).setScale(2, RoundingMode.HALF_UP);
        return lots2digits.doubleValue();
    }

    /**
     * 判断
     *
     * @param leaderCopier    跟随关系
     * @param orderInfo       交易信号
     * @param copierApiTrader 跟单者
     * @return PermitInfo
     */
    protected abstract PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, AbstractApiTrader copierApiTrader);


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PermitInfo {
        private Boolean permitted;
        private int permit;
        private String extra;
        private double lots;
    }
}

