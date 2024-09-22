package net.maku.subcontrol.rule;


import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.subcontrol.pojo.EaOrderInfo;
import net.maku.subcontrol.pojo.EaSymbolInfo;

/**
 * @author samson bruce
 */
public class FixedLot implements ICopyTradeStrategy {
    /**
     * @param eaLeaderCopier 跟随关系
     * @param eaOrderInfo    喊单者订单信息
     * @param lSymbolInfo    喊单者订单信息
     * @param fSymbolInfo    跟单者的SymbolInfo
     * @param equity         跟单者当前净值
     * @param currency       跟单者的计算货币
     * @return 跟单者应该开仓的手数
     */
    @Override
    public double lots(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, EaSymbolInfo lSymbolInfo, EaSymbolInfo fSymbolInfo, double equity, String currency) {
        return eaLeaderCopier.getFollowParam().doubleValue();
    }

    @Override
    public double lots(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, double equity, String currency) {
        return eaLeaderCopier.getFollowParam().doubleValue();
    }


}
