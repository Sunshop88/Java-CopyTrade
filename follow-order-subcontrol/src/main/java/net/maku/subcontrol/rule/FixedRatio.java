package net.maku.subcontrol.rule;


import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.subcontrol.pojo.EaSymbolInfo;
import org.springframework.util.ObjectUtils;

/**
 * @author X.T. LI
 */
@Slf4j
public class FixedRatio implements ICopyTradeStrategy {
    /**
     * 计算固定比率条件下跟单者的开仓手数
     *
     * @param eaLeaderCopier 跟随关系
     * @param eaOrderInfo    喊单者的订单信息
     * @param lSymbolInfo    喊单者的SymbolInfo
     * @param fSymbolInfo    跟单者的SymbolInfo
     * @param equity         跟单者当前净值
     * @param currency       跟单者的计算货币
     * @return 跟单者应该开仓的手数
     */
    @Override
    public double lots(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, EaSymbolInfo lSymbolInfo, EaSymbolInfo fSymbolInfo, double equity, String currency) {
        double slaveLots = eaOrderInfo.getLots() * eaLeaderCopier.getFollowParam().doubleValue() / 100;
        double ratio = ICopyTradeStrategy.lfRatio(lSymbolInfo, fSymbolInfo);
        if (!ObjectUtils.isEmpty(ratio)) {
            slaveLots = slaveLots * ratio;
        }
        return slaveLots;
    }

    @Override
    public double lots(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo,double equity, String currency) {
        return eaOrderInfo.getLots() * eaLeaderCopier.getFollowParam().doubleValue();
    }

}
