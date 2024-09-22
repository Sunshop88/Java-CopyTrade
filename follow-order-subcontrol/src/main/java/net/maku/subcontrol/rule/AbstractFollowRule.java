package net.maku.subcontrol.rule;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.subcontrol.util.SpringContextUtils;
import net.maku.subcontrol.pojo.EaOrderInfo;
import net.maku.subcontrol.pojo.EaSymbolInfo;
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
    /**
     * 链中下一元素
     */
    protected AbstractFollowRule nextRule = null;

    public void setNextRule(AbstractFollowRule nextRule) {
        this.nextRule = nextRule;
    }

    public PermitInfo rule(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, CopierApiTrader copierApiTrader){
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
     * @param masterSlave      订阅关系
     * @param eaOrderInfo      开仓订单信息
     * @param copierApiTrader 跟单者
     * @return double 跟单者开仓手数
     */
    public double lots(FollowTraderSubscribeEntity masterSlave, EaOrderInfo eaOrderInfo, CopierApiTrader copierApiTrader) throws InvalidSymbolException, ConnectException, TimeoutException {
        double lots = 0;
        if (ObjectUtils.isEmpty(masterSlave)) {
            return 0.0;
        }
        // TODO: 2023/4/30 如果跟单者没有这个品种需要在外部直接处理
        //喊单者、跟单者的合约大小
        EaSymbolInfo copierSymbolInfo = copierApiTrader.symbolInfo(eaOrderInfo.getSymbol(), true);
        EaSymbolInfo leaderSymbolInfo = null;
        //MT4平台
        LeaderApiTradersAdmin leader4ApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
        LeaderApiTrader LeaderApiTrader = leader4ApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(eaOrderInfo.getMasterId());
        leaderSymbolInfo = LeaderApiTrader.symbolInfo(eaOrderInfo.getOriSymbol(), true);
//            Leader5AbstractApiTradersAdmin leader5ApiTradersAdmin = SpringContextUtils.getBean(Leader5AbstractApiTradersAdmin.class);
//            Leader5ApiTrader leader5ApiTrader = leader5ApiTradersAdmin.getLeader5TraderConcurrentHashMap().get(eaOrderInfo.getMasterId());
//            lSymbolInfo = leader5ApiTrader.symbolInfo(eaOrderInfo.getOriSymbol(), true);

        log.debug("leaderSymbolInfo {}",leaderSymbolInfo);
        log.debug("copierSymbolInfo {}",copierSymbolInfo);
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
                break;
            default:
                lots = 0.0;
                break;
        }
        // 判断是否强制开仓，如果是强制开仓，那么开仓手数小于最小手数，会以最小手数开仓 -默认强制开仓
        boolean forceOpen = true;
        double mLots = forceOpen ? Math.max(lots, copierSymbolInfo.getMinVolume()) : lots;
        if (mLots >= copierSymbolInfo.getMinVolume()) {
            // 确保开仓手数是否符合步长，保证开仓手数是步长的整数倍。
            // 比如有些品种的步长是0.02,最小手数是0.02，那么开仓0.09就会失败，因为开仓手数必须是0.02的倍数
            // 加0.1是为了精度丢失
            int step = (int) (((mLots - copierSymbolInfo.getMinVolume()) / copierSymbolInfo.getStepVolume()) + 0.1);
            mLots = step * copierSymbolInfo.getStepVolume() + copierSymbolInfo.getMinVolume();
        } else {
            mLots = lots;
        }
        BigDecimal lots2digits = BigDecimal.valueOf(mLots).setScale(2, RoundingMode.HALF_UP);
        return lots2digits.doubleValue();
    }

    /**
     * 判断
     *
     * @param leaderCopier   跟随关系
     * @param orderInfo     交易信号
     * @param copierApiTrader 跟单者
     * @return PermitInfo
     */
    protected abstract PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, CopierApiTrader copierApiTrader) ;


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

